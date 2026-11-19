#!/usr/bin/env bash
# The peak sale (ADR-0001/0004/0005) on the platform with profile "scaling":
#   preflight -> stock refill -> Gatling (warm-up, ramp, peak, cool-down) with a disruption in the middle of the peak
#   -> reconciliation (no accepted order lost) -> recovery (lag to 0, replicas back to the minimum).
#   12-scaling-operations/scripts/peak.sh <scale|rollout|kill|drain>
# Env: PEAK_RATE (20 orders/s), WARM_RATE (2), PEAK_SECONDS (180), NAMESPACE (hephaestus-12),
#      ORDERS_URL (http://orders.localtest.me), GRAFANA_URL (http://grafana.localtest.me).
# Needs kubectl (context kind-hephaestus), curl, JDK 25. Exit 0 only if every check passed.
set -euo pipefail

SCENARIO="${1:?usage: peak.sh <scale|rollout|kill|drain>}"
case "$SCENARIO" in scale | rollout | kill | drain) ;; *) echo "unknown scenario: $SCENARIO" >&2; exit 2 ;; esac

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
# shellcheck source=lib.sh
. "$HERE/lib.sh"
NAMESPACE="${NAMESPACE:-hephaestus-12}"
ORDERS_URL="${ORDERS_URL:-http://orders.localtest.me}"
GRAFANA_URL="${GRAFANA_URL:-http://grafana.localtest.me}"
PEAK_RATE="${PEAK_RATE:-20}"
WARM_RATE="${WARM_RATE:-2}"
PEAK_SECONDS="${PEAK_SECONDS:-180}"
SERVICES="ordering payment inventory delivery"
TOPICS="ordering.events payment.events inventory.events delivery.events payment.commands inventory.commands delivery.commands ordering.replies"
BROKER="platform-dual-role-0"
RUN="$ROOT/12-scaling-operations/load/target/peak-$SCENARIO-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$RUN"
failed=0
drained=""

ok() { echo "ok    $*"; }
bad() {
    echo "FAIL  $*"
    failed=1
}
die() {
    echo "FAIL  $*" >&2
    exit 1
}
k() { kubectl -n "$NAMESPACE" "$@"; }
kafka() { kubectl -n kafka exec "$BROKER" -c kafka -- "$@"; }
min_replicas() { [ "$1" = "ordering" ] && echo 2 || echo 1; }
replicas() { k get deploy "$1" -o jsonpath='{.status.replicas}' 2>/dev/null || echo 0; }
# Total lag of the saga's consumer groups
total_lag() {
    kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --all-groups 2>/dev/null \
        | awk '$1 ~ /^(ordering|payment|inventory|delivery)$/ && $6 ~ /^[0-9]+$/ { sum += $6 } END { print sum + 0 }'
}
cleanup() {
    if [ -n "$drained" ]; then
        kubectl uncordon "$drained" >/dev/null 2>&1 || true
    fi
    jobs -p | xargs -r kill 2>/dev/null || true
}
trap cleanup EXIT

echo "==> preflight ($SCENARIO, peak $PEAK_RATE orders/s for ${PEAK_SECONDS}s, run files: $RUN)"
for s in $SERVICES; do
    k rollout status deploy/"$s" --timeout=300s >/dev/null || die "$s is not ready"
done
ok "4 services ready"
for t in $TOPICS; do
    n=$(kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic "$t" 2>/dev/null \
        | grep -c 'Partition: ' || true)
    # Fewer partitions cap the consumers below maxReplicaCount 6 (ADR-0002): the KafkaTopic must precede the services
    [ "$n" -eq 6 ] || die "topic $t has $n partitions, expected 6"
done
ok "8 topics with 6 partitions"
[ "$(curl -s -o /dev/null -w '%{http_code}' "$ORDERS_URL/api/orders/00000000-0000-0000-0000-000000000000")" = "404" ] \
    || die "$ORDERS_URL does not answer through the Gateway"
ok "Ordering reachable at $ORDERS_URL"

echo "==> stock refill"
k delete job stock-refill --ignore-not-found >/dev/null
k apply -f "$ROOT/12-scaling-operations/k8s-jobs/stock-refill.yaml" >/dev/null
k wait --for=condition=complete job/stock-refill --timeout=120s >/dev/null || die "stock refill failed"
ok "warehouse refilled"

if [ -f "$ROOT/12-scaling-operations/grafana/scaling.json" ]; then
    # Idempotent import of the scaling dashboard into the platform's Grafana (admin/admin)
    if curl -fs -u admin:admin -H 'Content-Type: application/json' -X POST "$GRAFANA_URL/api/dashboards/db" \
        -d "{\"overwrite\":true,\"dashboard\":$(cat "$ROOT/12-scaling-operations/grafana/scaling.json")}" >/dev/null; then
        ok "dashboard: $GRAFANA_URL/d/hephaestus-scaling"
    else
        echo "warn  dashboard import failed (Grafana at $GRAFANA_URL?)"
    fi
fi

echo "==> Gatling"
"$ROOT/mvnw" -B -q -f "$ROOT/12-scaling-operations/load/pom.xml" gatling:test \
    -DbaseUrl="$ORDERS_URL" -DwarmRate="$WARM_RATE" -DpeakRate="$PEAK_RATE" -DpeakSeconds="$PEAK_SECONDS" \
    -DordersFile="$RUN/orders.csv" > "$RUN/gatling.log" 2>&1 &
gatling=$!

# Replicas and lag every 10 s while the load runs
: > "$RUN/samples.txt"
(
    while kill -0 "$gatling" 2>/dev/null; do
        line="$(date +%H:%M:%S)"
        for s in $SERVICES; do line="$line $s=$(replicas "$s")"; done
        echo "$line lag=$(total_lag)" >> "$RUN/samples.txt"
        sleep 10
    done
) &

# The disruption in the middle of the peak: warm-up 60 s + ramp 30 s + half the peak
sleep $((60 + 30 + PEAK_SECONDS / 2))
case "$SCENARIO" in
    scale) echo "    (no disruption: autoscaling only)" ;;
    rollout)
        echo "    rollout restart of the four deployments (a new release in the middle of the peak)"
        k rollout restart deploy $SERVICES >/dev/null
        ;;
    kill)
        echo "    deleting one pod of each participant (payment: hard kill, as a crash)"
        for s in inventory delivery; do
            pod=$(k get pod -l app.kubernetes.io/name="$s" -o jsonpath='{.items[0].metadata.name}')
            k delete pod "$pod" --wait=false >/dev/null
        done
        pod=$(k get pod -l app.kubernetes.io/name=payment -o jsonpath='{.items[0].metadata.name}')
        k delete pod "$pod" --grace-period=0 --force >/dev/null 2>&1
        ;;
    drain)
        drained=$(kubectl get pods -A -o wide --no-headers | pick_drain_node) \
            || die "every worker hosts Kafka, lgtm or a database: nothing safe to drain"
        echo "    draining $drained"
        kubectl drain "$drained" --ignore-daemonsets --delete-emptydir-data --timeout=300s >/dev/null \
            || bad "drain of $drained did not finish (PDB?)"
        ;;
esac

gatling_exit=0
wait "$gatling" || gatling_exit=$?
if [ -n "$drained" ]; then
    kubectl uncordon "$drained" >/dev/null && drained=""
fi
report=$(ls -d "$ROOT"/12-scaling-operations/load/target/gatling/*/ 2>/dev/null | tail -1)
if [ "$gatling_exit" -eq 0 ]; then
    ok "Gatling assertions (p95 < 500 ms, p99 < 2 s, errors < 0.1 %) — report: ${report}index.html"
else
    bad "Gatling assertions failed (exit $gatling_exit) — see $RUN/gatling.log, report: ${report}index.html"
fi

echo "==> reconciliation"
if ORDERS_URL="$ORDERS_URL" NAMESPACE="$NAMESPACE" "$HERE/reconcile.sh" "$RUN/orders.csv" | tee "$RUN/reconcile.txt"; then
    ok "no accepted order lost"
else
    bad "reconciliation"
fi

echo "==> recovery"
deadline=$(($(date +%s) + 180))
until [ "$(total_lag)" -eq 0 ] || [ "$(date +%s)" -ge "$deadline" ]; do sleep 5; done
[ "$(total_lag)" -eq 0 ] && ok "lag back to 0" || bad "lag still $(total_lag) after 3 minutes"
deadline=$(($(date +%s) + 600))
while true; do
    at_min=true
    for s in $SERVICES; do [ "$(replicas "$s")" -le "$(min_replicas "$s")" ] || at_min=false; done
    $at_min && break
    [ "$(date +%s)" -ge "$deadline" ] && break
    sleep 15
done
$at_min && ok "replicas back to the minimum" || bad "replicas not back to the minimum after 10 minutes"

echo "==> summary"
for s in $SERVICES; do
    max=$(grep -o "$s=[0-9]*" "$RUN/samples.txt" | cut -d= -f2 | sort -n | tail -1)
    echo "    $s: max ${max:-?} replica(s)"
done
echo "    max lag: $(grep -o 'lag=[0-9]*' "$RUN/samples.txt" | cut -d= -f2 | sort -n | tail -1)"
[ "$failed" -eq 0 ] && echo "PEAK OK ($SCENARIO)" || { echo "PEAK FAILED ($SCENARIO)"; exit 1; }
