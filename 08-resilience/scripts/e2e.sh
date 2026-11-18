#!/usr/bin/env bash
# End-to-end comparison of the three resilience mechanisms in Docker Compose. Inventory reaches the supplier only
# through Toxiproxy; the script breaks the network (Toxiproxy) and the supplier (its /admin/faults) and shows, per
# mechanism, what the client got, how long it waited and how many calls actually reached the supplier.
#   08-resilience/scripts/e2e.sh          (SKIP_BUILD=true to reuse images, KEEP_STACK=true to keep it up)
# Needs JDK 25, Docker and curl.
set -euo pipefail

cd "$(dirname "$0")/.."   # 08-resilience
PROJECT=hephaestus-08
BASE="http://localhost:8080"
SUPPLIER="http://localhost:8090"
TOXI="http://localhost:8474"
BODY=/tmp/08-e2e-body.json
MECHANISMS="r4j sccb spring"

ok() { echo "ok    $*"; }
fail() {
    echo "FAIL  $*" >&2
    docker compose -p "$PROJECT" logs --tail=60 >&2 || true
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
calls() { curl -fs "$SUPPLIER/admin/stats" | sed -n 's/.*"offerCalls":\([0-9]*\).*/\1/p'; }
faults() { curl -fs -o /dev/null -X PUT -H 'Content-Type: application/json' -d "$1" "$SUPPLIER/admin/faults"; }
heal_supplier() { curl -fs -o /dev/null -X DELETE "$SUPPLIER/admin/faults"; }
toxic() { curl -fs -o /dev/null -H 'Content-Type: application/json' -d "$1" "$TOXI/proxies/supplier/toxics"; }
untoxic() { curl -s -o /dev/null -X DELETE "$TOXI/proxies/supplier/toxics/$1"; }

# One request: sets STATUS, SOURCE, MILLIS
ask() {
    local out
    out=$(curl -s -o "$BODY" -w '%{http_code} %{time_total}' "$BASE/api/supply/$1/$2")
    STATUS=${out%% *}
    MILLIS=$(awk -v s="${out##* }" 'BEGIN { printf "%d", s * 1000 }')
    SOURCE=$(sed -n 's/.*"source":"\([a-z]*\)".*/\1/p' "$BODY")
    SOURCE=${SOURCE:--}
}

TABLE=""
row() { TABLE+=$(printf '%-22s %-7s %-6s %-6s %8s ms %8s\n' "$1" "$2" "$3" "$4" "$5" "$6")$'\n'; }

if [ "${SKIP_BUILD:-false}" != "true" ]; then
    echo "==> images"
    ../mvnw -B -ntp -f pom.xml package jib:dockerBuild -DskipTests -Dspotless.check.skip=true
fi

echo "==> stack"
compose down -v >/dev/null 2>&1 || true
trap cleanup EXIT
compose up -d
for _ in $(seq 1 90); do
    if [ "$(code "$BASE/actuator/health/readiness")" = "200" ] && [ "$(code "$SUPPLIER/actuator/health")" = "200" ]; then
        break
    fi
    sleep 2
done
[ "$(code "$BASE/actuator/health/readiness")" = "200" ] || fail "inventory did not become ready"
ok "stack ready: inventory -> toxiproxy -> supplier, redis"

echo "==> healthy supplier: every mechanism answers live (and fills the cache)"
for m in $MECHANISMS; do
    ask "$m" ANVIL-01
    [ "$STATUS" = "200" ] && [ "$SOURCE" = "live" ] || fail "$m healthy: $STATUS $SOURCE"
    row "healthy" "$m" "$STATUS" "$SOURCE" "$MILLIS" "-"
done
ok "all live"
for m in $MECHANISMS; do
    ask "$m" NOPE-01
    [ "$STATUS" = "404" ] || fail "$m unknown offer must be 404, got $STATUS"
done
ok "unknown offer: 404 everywhere"

echo "==> network latency 1500 ms (Toxiproxy), read timeout is 1 s"
toxic '{"name":"latency","type":"latency","stream":"downstream","attributes":{"latency":1500}}'
for m in $MECHANISMS; do
    before=$(calls)
    ask "$m" ANVIL-01
    [ "$STATUS" = "200" ] && [ "$SOURCE" = "cache" ] || fail "$m latency: $STATUS $SOURCE"
    row "latency 1500 ms" "$m" "$STATUS" "$SOURCE" "$MILLIS" "$(($(calls) - before))"
done
untoxic latency
ok "latency: answered from the cache"

echo "==> supplier answers 503 to everything, 12 requests per mechanism"
faults '{"errorRate":1.0,"status":503,"delayMs":0}'
for m in $MECHANISMS; do
    before=$(calls)
    for _ in $(seq 1 12); do
        ask "$m" ANVIL-01
        [ "$STATUS" = "200" ] && [ "$SOURCE" = "cache" ] || fail "$m outage: $STATUS $SOURCE"
    done
    delta=$(($(calls) - before))
    row "503 x12 (last answer)" "$m" "$STATUS" "$SOURCE" "$MILLIS" "$delta"
    case "$m" in
        spring) [ "$delta" -eq 36 ] || fail "spring has no breaker: expected 36 supplier calls, got $delta" ;;
        r4j) [ "$delta" -le 12 ] || fail "r4j breaker should stop the calls: $delta supplier calls" ;;
        sccb) [ "$delta" -le 10 ] || fail "sccb breaker should stop the calls: $delta supplier calls" ;;
    esac
done
ok "outage: breakers stopped calling the supplier, Spring 7 retried every request"
for m in $MECHANISMS; do
    ask "$m" TONGS-01
    [ "$STATUS" = "503" ] || fail "$m: nothing cached during an outage must be 503, got $STATUS"
done
ok "nothing cached during the outage: 503"

echo "==> connection reset (Toxiproxy reset_peer)"
heal_supplier
toxic '{"name":"reset","type":"reset_peer","attributes":{"timeout":0}}'
for m in $MECHANISMS; do
    ask "$m" ANVIL-01
    [ "$STATUS" = "200" ] && [ "$SOURCE" = "cache" ] || fail "$m reset: $STATUS $SOURCE"
    row "reset_peer" "$m" "$STATUS" "$SOURCE" "$MILLIS" "-"
done
untoxic reset
ok "connection reset: answered from the cache"

echo "==> recovery: after the open interval (10 s) the breakers let probes through"
sleep 11
for m in $MECHANISMS; do
    ask "$m" ANVIL-01
    [ "$STATUS" = "200" ] && [ "$SOURCE" = "live" ] || fail "$m recovery: $STATUS $SOURCE"
    row "recovered" "$m" "$STATUS" "$SOURCE" "$MILLIS" "-"
done
ok "recovered: live again"

echo
printf '%-22s %-7s %-6s %-6s %11s %8s\n' "scenario" "mech" "status" "source" "time" "supplier"
printf '%s' "$TABLE"
echo
echo "E2E OK"
