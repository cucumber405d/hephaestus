#!/usr/bin/env bash
# End-to-end run of the order saga in both styles (choreography, orchestration) in Docker Compose:
# 1 happy path, 2 credit limit, 3 out of stock (refund), 4 delivery fails (release + refund), 5 Kafka down at order
# time (outbox delivers later), 6 duplicate first message, 7 participant down (orchestrator times out, choreography
# waits). Prints each order's saga history. On top of 10: every order is one trace in Tempo with spans of the services
# it went through (Delivery via the Java agent), the outbox and the timeout keep the trace, metrics reach Prometheus,
# logs reach Loki, the Grafana dashboard and alert rule are provisioned.
#   11-observability/scripts/e2e.sh          (SKIP_BUILD=true to reuse images, KEEP_STACK=true to keep it up)
# Needs JDK 25, Docker and curl. Takes a few minutes (Kafka restart, timeouts).
set -euo pipefail

cd "$(dirname "$0")/.."   # 11-observability
PROJECT=hephaestus-11
ORDERING="http://localhost:8080"
PAYMENT="http://localhost:8081"
INVENTORY="http://localhost:8082"
DELIVERY="http://localhost:8083"
GRAFANA="http://127.0.0.1:3000"
TEMPO="http://127.0.0.1:3200"
PROMETHEUS="http://127.0.0.1:9190"
LOKI="http://127.0.0.1:3100"
BODY=/tmp/11-e2e-body.json
STYLES="choreography orchestration"

ok() { echo "ok    $*"; }
fail() {
    echo "FAIL  $*" >&2
    docker compose -p "$PROJECT" logs --tail=40 >&2 || true
    exit 1
}
compose() { docker compose -p "$PROJECT" "$@"; }
cleanup() {
    if [ "${KEEP_STACK:-false}" != "true" ]; then
        compose down -v >/dev/null 2>&1 || true
    fi
    rm -f "$BODY"
}
code() { curl -s -o /dev/null -w '%{http_code}' "$@"; }
# order <customer> <saga> <sku> <qty> -> prints the order id
order() {
    local status
    status=$(curl -s -o "$BODY" -w '%{http_code}' -H 'Content-Type: application/json' \
        -d "{\"customerId\":\"$1\",\"saga\":\"$2\",\"lines\":[{\"sku\":\"$3\",\"quantity\":$4}]}" "$ORDERING/api/orders")
    [ "$status" = "202" ] || fail "order $*: $status $(cat "$BODY")"
    sed -n 's/^{"id":"\([^"]*\)".*/\1/p' "$BODY"
}
status() { curl -fs "$ORDERING/api/orders/$1" | sed -n 's/.*"status":"\([A-Z]*\)".*/\1/p'; }
reason() { curl -fs "$ORDERING/api/orders/$1" | sed -n 's/.*"reason":"\([^"]*\)".*/\1/p'; }
history() { curl -fs "$ORDERING/api/orders/$1" | grep -o '"entry":"[^"]*"' | sed 's/"entry":"\(.*\)"/        \1/'; }
# Waits up to $3 seconds for order $1 to reach status $2
wait_status() {
    for _ in $(seq 1 $(($3 * 2))); do
        [ "$(status "$1")" = "$2" ] && return 0
        sleep 0.5
    done
    return 1
}
# Waits until the history of order $1 contains $2 (compensations finish after the status is set)
wait_history() {
    for _ in $(seq 1 60); do
        history "$1" | grep -q "$2" && return 0
        sleep 0.5
    done
    return 1
}
# Trace ids of the traces with a span tagged with order $1 (Tempo search, last hour)
trace_ids() {
    curl -fsG "$TEMPO/api/search" --data-urlencode "q={ span.order.id = \"$1\" }" \
        --data-urlencode "start=$(($(date +%s) - 3600))" --data-urlencode "end=$(($(date +%s) + 60))" \
        --data-urlencode "limit=20" | grep -o '"traceID":"[0-9a-f]*"' | sed 's/.*:"\(.*\)"/\1/' \
        | while read -r id; do printf '%32s\n' "$id" | tr ' ' 0; done | sort -u   # Tempo drops leading zeros
}
# Service names of the spans in trace $1, sorted, space-separated
services() {
    curl -fs "$TEMPO/api/traces/$1" | grep -o '"key":"service.name","value":{"stringValue":"[^"]*"' \
        | sed 's/.*"stringValue":"\(.*\)"/\1/' | sort -u | tr '\n' ' ' | sed 's/ $//'
}
# Waits (telemetry is exported in batches) until order $1 is exactly one trace that has spans of each service in $2
# (space-separated; others may be there too: every participant consumes the events it then ignores); prints the id
wait_trace() {
    local ids="" have
    for _ in $(seq 1 60); do
        ids=$(trace_ids "$1" || true)
        if [ -n "$ids" ] && [ "$(echo "$ids" | wc -l)" -eq 1 ]; then
            have=" $(services "$ids") "
            if all_in "$have" $2; then
                echo "$ids"
                return 0
            fi
        fi
        sleep 2
    done
    echo "order $1: traces [$(echo $ids)]" >&2
    [ -n "$ids" ] && echo "  services of the first: [$(services "$(echo "$ids" | head -1)")]" >&2
    return 1
}
# Waits until the PromQL query $1 has a result
wait_metric() {
    for _ in $(seq 1 45); do
        curl -fsG "$PROMETHEUS/api/v1/query" --data-urlencode "query=$1" | grep -q '"result":\[{' && return 0
        sleep 2
    done
    return 1
}
# all_in " <list> " <word>...: every word is in the list
all_in() {
    local list="$1" word
    shift
    for word in "$@"; do
        case "$list" in *" $word "*) ;; *) return 1 ;; esac
    done
}
ALL="delivery inventory ordering payment"
faults() { curl -fs -o /dev/null -X PUT -H 'Content-Type: application/json' -d "$2" "$1/admin/faults"; }
heal() { for s in "$PAYMENT" "$INVENTORY" "$DELIVERY"; do curl -fs -o /dev/null -X DELETE "$s/admin/faults"; done; }
wait_ready() {
    for url in "$@"; do
        for _ in $(seq 1 90); do
            [ "$(code "$url/actuator/health/readiness")" = "200" ] && break
            sleep 2
        done
        [ "$(code "$url/actuator/health/readiness")" = "200" ] || fail "$url not ready"
    done
}

if [ "${SKIP_BUILD:-false}" != "true" ]; then
    echo "==> images"
    ../mvnw -B -ntp -f pom.xml package jib:dockerBuild -DskipTests -Dspotless.check.skip=true
fi

echo "==> stack"
compose down -v >/dev/null 2>&1 || true
trap cleanup EXIT
# 60 s step timeout: scenario 5 restarts Kafka, which with the outbox resubmission takes longer than 30 s
export SAGA_STEP_TIMEOUT=60s
compose up -d
wait_ready "$ORDERING" "$PAYMENT" "$INVENTORY" "$DELIVERY"
for _ in $(seq 1 60); do
    [ "$(code "$GRAFANA/api/health")" = "200" ] && break
    sleep 2
done
[ "$(code "$GRAFANA/api/health")" = "200" ] || fail "Grafana not ready"
ok "stack ready (Kafka UI: http://localhost:8085, Grafana: $GRAFANA)"

for style in $STYLES; do
    echo "==> [$style] 1. happy path"
    id=$(order alice "$style" HAMMER-01 1)
    wait_status "$id" CONFIRMED 30 || fail "$style happy path: $(status "$id")"
    history "$id"
    ok "$style: CONFIRMED"
    trace=$(wait_trace "$id" "$ALL") || fail "$style happy path: not one trace through all four services"
    ok "$style: one trace $trace with spans of $ALL"

    echo "==> [$style] 2. credit limit (bob, 50 EUR)"
    id=$(order bob "$style" ANVIL-01 1)
    wait_status "$id" REJECTED 30 || fail "$style credit limit: $(status "$id")"
    [ "$(reason "$id")" = "credit limit exceeded" ] || fail "$style credit limit reason: $(reason "$id")"
    ok "$style: REJECTED at payment, no compensation needed"
    wait_trace "$id" "ordering payment" >/dev/null || fail "$style credit limit: not one trace (ordering, payment)"
    ok "$style: the rejection is one trace"

    echo "==> [$style] 3. out of stock -> refund"
    id=$(order alice "$style" TONGS-01 1)
    wait_status "$id" REJECTED 30 || fail "$style out of stock: $(status "$id")"
    wait_history "$id" "PaymentRefunded" || fail "$style: payment was not refunded"
    history "$id"
    ok "$style: REJECTED at stock, payment refunded"

    echo "==> [$style] 4. delivery fails -> release + refund"
    faults "$DELIVERY" '{"failCustomers":["carol"],"delayMs":0}'
    id=$(order carol "$style" HAMMER-01 1)
    wait_status "$id" REJECTED 30 || fail "$style delivery failure: $(status "$id")"
    wait_history "$id" "StockReleased" || fail "$style: stock was not released"
    wait_history "$id" "PaymentRefunded" || fail "$style: payment was not refunded"
    heal
    history "$id"
    ok "$style: REJECTED at delivery, stock released and payment refunded"

    echo "==> [$style] 6. duplicate first message"
    id=$(order alice "$style" HAMMER-01 1)
    wait_status "$id" CONFIRMED 30 || fail "$style before duplicate: $(status "$id")"
    dups=$(curl -fs "$PAYMENT/admin/stats" | sed -n 's/.*"duplicates":\([0-9]*\).*/\1/p')
    [ "$(code -X POST "$ORDERING/admin/republish/$id")" = "202" ] || fail "republish"
    for _ in $(seq 1 30); do
        [ "$(curl -fs "$PAYMENT/admin/stats" | sed -n 's/.*"duplicates":\([0-9]*\).*/\1/p')" -gt "$dups" ] && break
        sleep 0.5
    done
    [ "$(curl -fs "$PAYMENT/admin/stats" | sed -n 's/.*"duplicates":\([0-9]*\).*/\1/p')" -gt "$dups" ] \
        || fail "$style: duplicate not detected by Payment"
    [ "$(status "$id")" = "CONFIRMED" ] || fail "$style: duplicate changed the order"
    ok "$style: re-sent first message recognised as duplicate, nothing happened twice"
done

echo "==> 5. Kafka down at order time: the outbox keeps the message"
compose stop kafka >/dev/null
choreo=$(order alice choreography HAMMER-01 1)
orch=$(order alice orchestration HAMMER-01 1)
ok "orders accepted (202) while Kafka is down: $choreo, $orch"
pending=$(docker exec "$(compose ps -q postgres)" psql -U postgres -d ordering -tAc \
    "select count(*) from event_publication where completion_date is null" | tr -d ' ')
[ "${pending:-0}" -ge 2 ] || fail "outbox should hold the unsent messages, pending=$pending"
ok "outbox holds $pending unsent publication(s)"
sleep 5
compose start kafka >/dev/null
wait_status "$choreo" CONFIRMED 120 || fail "choreography after Kafka restart: $(status "$choreo")"
wait_status "$orch" CONFIRMED 120 || fail "orchestration after Kafka restart: $(status "$orch")"
ok "both sagas completed after Kafka came back"
wait_trace "$choreo" "$ALL" >/dev/null || fail "choreography after Kafka restart: the outbox broke the trace"
wait_trace "$orch" "$ALL" >/dev/null || fail "orchestration after Kafka restart: the outbox broke the trace"
ok "outbox resubmission kept each saga in one trace"

echo "==> 7. Delivery down"
compose stop delivery >/dev/null
choreo=$(order alice choreography HAMMER-01 1)
orch=$(order alice orchestration HAMMER-01 1)
wait_status "$orch" REJECTED 90 || fail "orchestration should time out: $(status "$orch")"
[ "$(reason "$orch")" = "timeout at step delivery" ] || fail "timeout reason: $(reason "$orch")"
ok "orchestration: timeout at delivery -> REJECTED, compensations sent"
t=""
for _ in $(seq 1 30); do
    t=$(trace_ids "$orch" || true)
    if [ -n "$t" ] && [ "$(echo "$t" | wc -l)" -eq 1 ] && curl -fs "$TEMPO/api/traces/$t" | grep -q '"name":"saga timeout"'; then
        break
    fi
    t=""
    sleep 2
done
[ -n "$t" ] || fail "the timeout should continue the saga's single trace"
ok "timeout and compensations are inside the saga's trace $t"

[ "$(status "$choreo")" = "PENDING" ] || fail "choreography should still wait: $(status "$choreo")"
ok "choreography: still PENDING (nobody keeps a deadline)"
compose start delivery >/dev/null
wait_ready "$DELIVERY"
wait_status "$choreo" CONFIRMED 60 || fail "choreography after Delivery came back: $(status "$choreo")"
wait_history "$orch" "DeliveryCancelled" || fail "late delivery of the timed-out order was not cancelled"
history "$orch"
ok "choreography completed late; orchestration's late delivery was cancelled"

echo "==> telemetry: metrics, logs, Grafana"
wait_metric 'sum by (style) (saga_completed_total{outcome="rejected"})' || fail "saga_completed_total not in Prometheus"
wait_metric 'saga_duration_seconds_bucket' || fail "saga_duration_seconds not in Prometheus"
wait_metric 'outbox_publications_incomplete' || fail "outbox_publications_incomplete not in Prometheus"
ok "saga and outbox metrics in Prometheus"
logs=""
for _ in $(seq 1 30); do
    logs=$(curl -fsG "$LOKI/loki/api/v1/query_range" \
        --data-urlencode "query={service_name=\"ordering\"} | trace_id=\"$t\"" \
        --data-urlencode "start=$(($(date +%s) - 3600))000000000" --data-urlencode "limit=5" || true)
    echo "$logs" | grep -q '"values":\[\[' && break
    logs=""
    sleep 2
done
[ -n "$logs" ] || fail "no ordering logs in Loki for trace $t"
ok "Loki finds ordering's logs by trace id"
curl -fs -u admin:admin "$GRAFANA/api/search?query=Saga" | grep -q '"uid":"hephaestus-saga"' || fail "dashboard not provisioned"
curl -fs -u admin:admin "$GRAFANA/api/v1/provisioning/alert-rules" | grep -q '"title":"Outbox backlog"' \
    || fail "alert rule not provisioned"
ok "Grafana: dashboard Saga and alert rule Outbox backlog provisioned"

echo "E2E OK"

