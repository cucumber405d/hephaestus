#!/usr/bin/env bash
# End-to-end run of the order saga in both styles (choreography, orchestration) in Docker Compose:
# 1 happy path, 2 credit limit, 3 out of stock (refund), 4 delivery fails (release + refund), 5 Kafka down at order
# time (outbox delivers later), 6 duplicate first message, 7 participant down (orchestrator times out, choreography
# waits). Prints each order's saga history.
#   11-observability/scripts/e2e.sh          (SKIP_BUILD=true to reuse images, KEEP_STACK=true to keep it up)
# Needs JDK 25, Docker and curl. Takes a few minutes (Kafka restart, timeouts).
set -euo pipefail

cd "$(dirname "$0")/.."   # 11-observability
PROJECT=hephaestus-11
ORDERING="http://localhost:8080"
PAYMENT="http://localhost:8081"
INVENTORY="http://localhost:8082"
DELIVERY="http://localhost:8083"
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
ok "stack ready (Kafka UI: http://localhost:8085)"

for style in $STYLES; do
    echo "==> [$style] 1. happy path"
    id=$(order alice "$style" HAMMER-01 1)
    wait_status "$id" CONFIRMED 30 || fail "$style happy path: $(status "$id")"
    history "$id"
    ok "$style: CONFIRMED"

    echo "==> [$style] 2. credit limit (bob, 50 EUR)"
    id=$(order bob "$style" ANVIL-01 1)
    wait_status "$id" REJECTED 30 || fail "$style credit limit: $(status "$id")"
    [ "$(reason "$id")" = "credit limit exceeded" ] || fail "$style credit limit reason: $(reason "$id")"
    ok "$style: REJECTED at payment, no compensation needed"

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

echo "==> 7. Delivery down"
compose stop delivery >/dev/null
choreo=$(order alice choreography HAMMER-01 1)
orch=$(order alice orchestration HAMMER-01 1)
wait_status "$orch" REJECTED 90 || fail "orchestration should time out: $(status "$orch")"
[ "$(reason "$orch")" = "timeout at step delivery" ] || fail "timeout reason: $(reason "$orch")"
ok "orchestration: timeout at delivery -> REJECTED, compensations sent"
[ "$(status "$choreo")" = "PENDING" ] || fail "choreography should still wait: $(status "$choreo")"
ok "choreography: still PENDING (nobody keeps a deadline)"
compose start delivery >/dev/null
wait_ready "$DELIVERY"
wait_status "$choreo" CONFIRMED 60 || fail "choreography after Delivery came back: $(status "$choreo")"
wait_history "$orch" "DeliveryCancelled" || fail "late delivery of the timed-out order was not cancelled"
history "$orch"
ok "choreography completed late; orchestration's late delivery was cancelled"

echo "E2E OK"
