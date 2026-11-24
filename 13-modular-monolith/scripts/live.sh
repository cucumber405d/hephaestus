#!/usr/bin/env bash
# The e2e run against the kind cluster, through the Envoy Gateway and its HTTPRoute (WO- -> monolith, the rest of
# the smith API -> forge-service): before -> cutover -> drain -> decommission, then the reconciliation.
#   13-modular-monolith/scripts/live.sh          (after scripts/deploy-local.sh)
# Env: GATEWAY (127.0.0.1), RATE (2), BEFORE_S (60), AFTER_S (60), NAMESPACE (hephaestus-13).
set -euo pipefail

cd "$(dirname "$0")/.."   # 13-modular-monolith
NAMESPACE="${NAMESPACE:-hephaestus-13}"
GATEWAY="${GATEWAY:-127.0.0.1}"
HOST=workshop.localtest.me
RATE="${RATE:-2}"
BEFORE_S="${BEFORE_S:-60}"
AFTER_S="${AFTER_S:-60}"
RUN="target/live-$(date +%Y%m%d-%H%M%S)"
export MONOLITH_URL="http://$GATEWAY" HOST_HEADER="$HOST"
mkdir -p "$RUN"

ok() { echo "ok    $*"; }
fail() { echo "FAIL  $*" >&2; exit 1; }
code() { curl -s -o /dev/null -w '%{http_code}' -H "Host: $HOST" "$@"; }
psql_on() { # cluster database sql
    kubectl -n "$NAMESPACE" exec "$1-1" -c postgres -- psql -U postgres -d "$2" -At -c "$3"
}
BOT=""
GEN=""
trap '[ -n "$BOT" ] && kill "$BOT" 2>/dev/null; [ -n "$GEN" ] && kill "$GEN" 2>/dev/null; true' EXIT

[ -f traffic/target/traffic.jar ] || ../mvnw -B -ntp -q -f pom.xml -pl traffic package -DskipTests
[ "$(code "http://$GATEWAY/admin/forge")" = "200" ] || fail "monolith not reachable through the Gateway"
[ "$(code "http://$GATEWAY/api/work-orders")" = "200" ] || fail "forge-service not reachable through the Gateway"
ok "both reachable through http://$HOST"

scripts/cutover.sh before > /dev/null
GATEWAY_URL="http://$GATEWAY" INTERVAL_MS=500 java -jar traffic/target/traffic.jar bot > "$RUN/bot.log" 2>&1 &
BOT=$!
ORDERS_URL="http://$GATEWAY" RATE=$RATE SECONDS=$((BEFORE_S + AFTER_S)) OUT="$RUN/orders.csv" \
    java -jar traffic/target/traffic.jar generate > "$RUN/generator.log" 2>&1 &
GEN=$!
echo "==> before (${BEFORE_S}s)"
sleep "$BEFORE_S"
scripts/cutover.sh cutover > /dev/null || fail "cutover"
ok "cut over at $(date +%T); orders keep coming for ${AFTER_S}s"
wait "$GEN" || fail "generator"
GEN=""
ok "generator: $(tail -1 "$RUN/generator.log")"
DRAIN_TIMEOUT=300 scripts/cutover.sh drain || fail "drain"
scripts/cutover.sh decommission > /dev/null || fail "decommission"
[ "$(code "http://$GATEWAY/api/work-orders/WO-000001/start" -X POST)" = "410" ] \
    || fail "WO- routes still reach a live forge after decommission"
ok "decommissioned: WO- routes answer 410 (remove that rule from k8s/route.yaml next)"

ORDERS_DUMP_CMD="kubectl -n $NAMESPACE exec monolith-db-1 -c postgres -- psql -U postgres -d workshop -At -c \"select id, status, coalesce(reason, '') from ordering.customer_order where custom and customer_id like 'cutover-%'\"" \
MONOLITH_WO_DUMP_CMD="kubectl -n $NAMESPACE exec monolith-db-1 -c postgres -- psql -U postgres -d workshop -At -c \"select order_id, id, status from forge.work_order\"" \
SERVICE_WO_DUMP_CMD="kubectl -n $NAMESPACE exec forge-db-1 -c postgres -- psql -U postgres -d forge -At -c \"select order_id, id, status from work_order\"" \
    WAIT_S=180 scripts/reconcile.sh "$RUN/orders.csv" | tee "$RUN/reconcile.txt" || fail "reconciliation"
ok "work orders: monolith $(psql_on monolith-db workshop 'select count(*) from forge.work_order' | tr -d '\r'), forge-service $(psql_on forge-db forge 'select count(*) from work_order' | tr -d '\r')"
echo "LIVE OK: run files in 13-modular-monolith/$RUN"
