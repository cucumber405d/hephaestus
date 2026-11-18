#!/usr/bin/env bash
# End-to-end run of the 09 scenarios in Docker Compose: happy path, order per customer, transient failure with
# retries, permanent failure -> DLT -> replay, duplicate event, scaling of the consumer group, share group.
#   09-events-kafka/scripts/e2e.sh          (SKIP_BUILD=true to reuse images, KEEP_STACK=true to keep it up)
# Needs JDK 25, Docker and curl. Consumer lag (scenario 8) is for trying by hand, see docs/start.md.
set -euo pipefail

cd "$(dirname "$0")/.."   # 09-events-kafka
PROJECT=hephaestus-09
ORDERING="http://localhost:8080"
NOTIFY="http://localhost:8091"
BODY=/tmp/09-e2e-body.json

ok() { echo "ok    $*"; }
fail() {
    echo "FAIL  $*" >&2
    docker compose -p "$PROJECT" --profile share logs --tail=40 >&2 || true
    exit 1
}
compose() { docker compose -p "$PROJECT" "$@"; }
cleanup() {
    if [ "${KEEP_STACK:-false}" != "true" ]; then
        compose --profile share down -v >/dev/null 2>&1 || true
    fi
    rm -f "$BODY"
}
code() { curl -s -o /dev/null -w '%{http_code}' "$@"; }
# Places an order for the customer; prints the order id
order() {
    local status
    status=$(curl -s -o "$BODY" -w '%{http_code}' -H 'Content-Type: application/json' \
        -d "{\"customerId\":\"$1\",\"lines\":[{\"sku\":\"ANVIL-01\",\"quantity\":1}]}" "$ORDERING/api/orders")
    [ "$status" = "201" ] || fail "order for $1: $status $(cat "$BODY")"
    sed -n 's/^{"id":"\([^"]*\)".*/\1/p' "$BODY"
}
notifications() { curl -fs "$NOTIFY/api/notifications?customerId=$1" | grep -o '"orderId"' | wc -l | tr -d ' '; }
# Waits up to $2 seconds until customer $1 has $3 notifications
wait_notified() {
    for _ in $(seq 1 $(($2 * 2))); do
        [ "$(notifications "$1")" = "$3" ] && return 0
        sleep 0.5
    done
    return 1
}
faults() { curl -fs -o /dev/null -X PUT -H 'Content-Type: application/json' -d "$2" "$1/admin/faults"; }
stat_field() { curl -fs "$1/admin/stats" | sed -n "s/.*\"$2\":\([0-9]*\).*/\1/p"; }
partitions_of() { curl -fs "$1/admin/stats" | sed -n 's/.*"partitions":\[\([0-9,]*\)\].*/\1/p'; }
# Host ports of the running instances of a service
ports_of() {
    compose ps --format '{{.Ports}}' "$1" 2>/dev/null \
        | grep -o '0.0.0.0:[0-9]*->8080' | sed 's/0.0.0.0:\([0-9]*\)->8080/\1/' | sort -u
}
wait_ready() {
    for port in $(ports_of "$1"); do
        for _ in $(seq 1 90); do
            [ "$(code "http://localhost:$port/actuator/health/readiness")" = "200" ] && break
            sleep 2
        done
    done
}

if [ "${SKIP_BUILD:-false}" != "true" ]; then
    echo "==> images"
    ../mvnw -B -ntp -f pom.xml package jib:dockerBuild -DskipTests -Dspotless.check.skip=true
fi

echo "==> stack"
compose --profile share down -v >/dev/null 2>&1 || true
trap cleanup EXIT
compose up -d
for _ in $(seq 1 90); do
    if [ "$(code "$ORDERING/actuator/health/readiness")" = "200" ] && [ "$(code "$NOTIFY/actuator/health/readiness")" = "200" ]; then
        break
    fi
    sleep 2
done
[ "$(code "$NOTIFY/actuator/health/readiness")" = "200" ] || fail "stack did not become ready"
ok "stack ready (Kafka UI: http://localhost:8085)"

echo "==> 1. happy path"
order alice >/dev/null
wait_notified alice 30 1 || fail "alice was not notified"
ok "order -> OrderPlaced -> notification"

echo "==> 2. order per customer (one key, one partition)"
expected=""
for _ in 1 2 3 4 5; do expected+="$(order erin) "; done
wait_notified erin 30 5 || fail "erin: expected 5 notifications"
actual=$(curl -fs "$NOTIFY/api/notifications?customerId=erin" | grep -o '"orderId":"[^"]*"' | sed 's/"orderId":"\([^"]*\)"/\1/' | tr '\n' ' ')
[ "$actual" = "$expected" ] || fail "erin's notifications out of order: $actual vs $expected"
ok "5 orders of one customer notified in order"

echo "==> 3. transient failure: 2 failed attempts, then retries succeed"
faults "$NOTIFY" '{"failCustomers":[],"transientFailures":2,"delayMs":0}'
started=$(date +%s)
order frank >/dev/null
wait_notified frank 30 1 || fail "frank was not notified after retries"
elapsed=$(($(date +%s) - started))
[ "$elapsed" -ge 3 ] || fail "retries should take >= 3 s (1 s + 2 s backoff), took ${elapsed}s"
ok "notified after retries in ${elapsed}s"

echo "==> 4. permanent failure -> DLT -> replay"
faults "$NOTIFY" '{"failCustomers":["bob"],"transientFailures":0,"delayMs":0}'
order bob >/dev/null
order carol >/dev/null
wait_notified carol 30 1 || fail "carol must not be blocked by bob's failure"
sleep 9   # 1 + 2 + 4 s of retries, then the DLT
[ "$(notifications bob)" = "0" ] || fail "bob must not be notified while failing"
curl -fs -o /dev/null -X DELETE "$NOTIFY/admin/faults"
replayed=$(curl -fs -X POST "$NOTIFY/admin/dlt/replay" | sed -n 's/.*"replayed":\([0-9]*\).*/\1/p')
[ "${replayed:-0}" -ge 1 ] || fail "replay should resend bob's event, replayed=$replayed"
wait_notified bob 30 1 || fail "bob not notified after replay"
ok "bob's event: retries -> DLT -> fixed -> replayed ($replayed) -> notified; carol was not blocked"

echo "==> 5. duplicate event"
alice_order=$(order alice)
wait_notified alice 30 2 || fail "alice's second order"
dup_before=$(stat_field "$NOTIFY" duplicates)
[ "$(code -X POST "$ORDERING/admin/republish/$alice_order")" = "202" ] || fail "republish"
for _ in $(seq 1 30); do [ "$(stat_field "$NOTIFY" duplicates)" -gt "$dup_before" ] && break; sleep 0.5; done
[ "$(stat_field "$NOTIFY" duplicates)" -gt "$dup_before" ] || fail "duplicate was not detected"
[ "$(notifications alice)" = "2" ] || fail "duplicate must not notify again"
ok "same eventId twice: one notification, duplicate counted"

echo "==> 6. scaling the consumer group"
compose up -d --scale notification=3 notification >/dev/null
wait_ready notification
sleep 10   # rebalance
owners=0
all=""
for port in $(ports_of notification); do
    p=$(partitions_of "http://localhost:$port")
    [ -n "$p" ] && owners=$((owners + 1)) && all+="$p,"
done
[ "$owners" = "3" ] || fail "3 instances should own one partition each, owners=$owners ($all)"
[ "$(echo "$all" | tr ',' '\n' | grep -c .)" = "3" ] || fail "partitions 0,1,2 expected once each: $all"
ok "3 instances, partitions [$all] one each"
compose up -d --scale notification=5 notification >/dev/null
wait_ready notification
sleep 10
idle=0
for port in $(ports_of notification); do
    [ -z "$(partitions_of "http://localhost:$port")" ] && idle=$((idle + 1))
done
[ "$idle" = "2" ] || fail "with 5 members and 3 partitions 2 must be idle, idle=$idle"
ok "5 instances: 2 idle (more members than partitions)"
compose up -d --scale notification=1 notification >/dev/null

echo "==> 7. share group: 5 members on 3 partitions all get work"
compose --profile share up -d --scale notification-share=5 >/dev/null
wait_ready notification-share
sleep 10   # members join; a new share group starts at the latest offset
for port in $(ports_of notification-share); do
    faults "http://localhost:$port" '{"failCustomers":[],"transientFailures":0,"delayMs":300}'
done
for i in $(seq 1 30); do order "share-$i" >/dev/null; done
sleep 15
busy=0
total=0
for port in $(ports_of notification-share); do
    n=$(stat_field "http://localhost:$port" processed)
    total=$((total + n))
    [ "$n" -gt 0 ] && busy=$((busy + 1))
done
[ "$total" -ge 30 ] || fail "share group handled $total of 30 events"
[ "$busy" -ge 4 ] || fail "share group: expected work on more than 3 members, busy=$busy"
ok "share group: $total events spread over $busy of 5 members (classic group: at most 3)"

echo "E2E OK"
