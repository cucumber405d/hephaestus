#!/usr/bin/env bash
# End-to-end test of the 06 stack in Docker Compose, through the Gateway only (what a client would see):
# API versions, ordering, business errors, the hidden internal API, and an Inventory outage (503 + Retry-After).
#   06-service-communication/scripts/e2e.sh          (SKIP_BUILD=true to reuse images, KEEP_STACK=true to keep it up)
# Needs JDK 25 and Docker.
set -euo pipefail

cd "$(dirname "$0")/.."   # 06-service-communication
PROJECT=hephaestus-06
BASE="http://localhost:${GATEWAY_PORT:-8080}"

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
}
# status code of a request; extra curl arguments pass through
code() { curl -s -o /dev/null -w '%{http_code}' "$@"; }
order() { curl -s -D - -o /tmp/06-e2e-body.json -H 'Content-Type: application/json' -d "$1" "$BASE/api/orders"; }

if [ "${SKIP_BUILD:-false}" != "true" ]; then
    echo "==> images"
    ../mvnw -B -ntp -f pom.xml package jib:dockerBuild -DskipTests -Dspotless.check.skip=true
fi

echo "==> stack"
compose down -v >/dev/null 2>&1 || true
trap cleanup EXIT
compose up -d
for _ in $(seq 1 90); do
    if [ "$(code -H 'API-Version: 2' "$BASE/api/stock/ANVIL-01")" = "200" ] \
        && [ "$(code "$BASE/api/orders/availability/ANVIL-01")" = "200" ]; then
        break
    fi
    sleep 2
done
[ "$(code "$BASE/api/orders/availability/ANVIL-01")" = "200" ] || fail "stack did not become ready"
ok "stack ready behind the gateway"

echo "==> API versions (Inventory, through the gateway)"
headers=$(curl -s -D - -o /tmp/06-e2e-body.json -H 'API-Version: 1' "$BASE/api/stock/ANVIL-01")
grep -q '"available":10' /tmp/06-e2e-body.json || fail "v1 body: $(cat /tmp/06-e2e-body.json)"
grep -q 'onHand' /tmp/06-e2e-body.json && fail "v1 must not show onHand"
echo "$headers" | grep -qi '^Deprecation:' || fail "v1 must carry a Deprecation header"
echo "$headers" | grep -qi '^X-Gateway: hephaestus' || fail "missing X-Gateway header"
ok "v1: available only, Deprecation header"
curl -fs -H 'API-Version: 2' "$BASE/api/stock/ANVIL-01" | grep -q '"onHand":10' || fail "v2 body"
ok "v2: onHand/reserved/available"
[ "$(code -H 'API-Version: 3' "$BASE/api/stock/ANVIL-01")" = "400" ] || fail "unsupported version must be 400"
ok "v3: 400"

echo "==> orders"
headers=$(order '{"lines":[{"sku":"ANVIL-01","quantity":2}]}')
echo "$headers" | head -1 | grep -q ' 201' || fail "place order: $(echo "$headers" | head -1) $(cat /tmp/06-e2e-body.json)"
location=$(echo "$headers" | tr -d '\r' | sed -n 's/^[Ll]ocation: *//p')
path=$(echo "$location" | sed 's#^https\{0,1\}://[^/]*##')
curl -fs "$BASE$path" | grep -q '"status":"CONFIRMED"' || fail "GET $path"
ok "POST /api/orders 201, GET $path CONFIRMED (Location: $location)"
curl -fs "$BASE/api/orders/availability/ANVIL-01" | grep -q '"available":8' || fail "availability after order"
ok "availability 10 -> 8"

# Capture first: piping curl -D - straight into head would SIGPIPE curl and, with pipefail, fail a passing check
headers=$(order '{"lines":[{"sku":"TONGS-01","quantity":1}]}')
echo "$headers" | head -1 | grep -q ' 409' || fail "out of stock must be 409"
grep -q '"sku":"TONGS-01"' /tmp/06-e2e-body.json || fail "409 must name the SKU"
ok "out of stock: 409 insufficient-stock"
headers=$(order '{"lines":[{"sku":"NOPE-01","quantity":1}]}')
echo "$headers" | head -1 | grep -q ' 422' || fail "unknown SKU must be 422"
ok "unknown SKU: 422"
[ "$(code -H 'Content-Type: application/json' -d '{}' "$BASE/api/reservations")" = "404" ] \
    || fail "internal /api/reservations must not be reachable through the gateway"
ok "internal reservation API: 404 at the gateway"

echo "==> Inventory outage"
compose stop inventory >/dev/null
started=$(date +%s)
headers=$(order '{"lines":[{"sku":"HAMMER-01","quantity":1}]}')
elapsed=$(($(date +%s) - started))
echo "$headers" | head -1 | grep -q ' 503' || fail "outage must be 503: $(echo "$headers" | head -1)"
echo "$headers" | grep -qi '^Retry-After: 5' || fail "503 must carry Retry-After"
[ "$elapsed" -le 5 ] || fail "outage answer took ${elapsed}s; timeouts must keep it short"
ok "503 inventory-unavailable with Retry-After in ${elapsed}s"
compose start inventory >/dev/null

echo "E2E OK"
