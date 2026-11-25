#!/usr/bin/env bash
# 14 end to end in Docker Compose (CI): a custom order is quoted by Forge over gRPC, accepted with price and lead
# time, travels as Avro through Apicurio, and becomes READY when the smith completes it; refusals carry their reason;
# without Forge the order is rejected at once ("quote unavailable").
#   15-cdc-debezium/scripts/e2e.sh     (SKIP_BUILD=true reuses images, KEEP_STACK=true keeps the stack up)
# Needs JDK 25, Docker, curl, python3.
set -euo pipefail

cd "$(dirname "$0")/.."   # 15-cdc-debezium
PROJECT=hephaestus-15
ORDERING=http://localhost:18080
FORGE=http://localhost:18081
REGISTRY=http://localhost:18085
RUN="target/e2e-$(date +%Y%m%d-%H%M%S)"

ok() { echo "ok    $*"; }
fail() {
    echo "FAIL  $*" >&2
    compose logs --tail=60 ordering forge-service schema-init >&2 || true
    exit 1
}
compose() { docker compose -f compose.yaml -p "$PROJECT" "$@"; }
code() { curl -s -o /dev/null -w '%{http_code}' "$@"; }
# json <path> reads a value from stdin, e.g. json status, json 0.id
json() { python3 -c 'import json,sys
v=json.load(sys.stdin)
for k in sys.argv[1].split("."):
    v=v[int(k)] if isinstance(v,list) else v.get(k)
print("" if v is None else v)' "$1"; }
cleanup() {
    if [ "${KEEP_STACK:-false}" != "true" ]; then
        compose down -v > /dev/null 2>&1 || true
    fi
}
trap cleanup EXIT
mkdir -p "$RUN"

place() {
    curl -sf -X POST "$ORDERING/api/orders" -H 'Content-Type: application/json' -d "$1" | json id
}
order() { curl -sf "$ORDERING/api/orders/$1"; }
# wait_status <order> <status> <seconds>
wait_status() {
    for _ in $(seq 1 "$3"); do
        [ "$(order "$1" | json status)" = "$2" ] && return 0
        sleep 1
    done
    fail "order $1 is $(order "$1" | json status), not $2 (reason: $(order "$1" | json reason))"
}

if [ "${SKIP_BUILD:-false}" != "true" ]; then
    echo "==> build (images)"
    ../mvnw -B -ntp -f pom.xml package jib:dockerBuild -DskipTests -Dspotless.check.skip=true > "$RUN/build.log" 2>&1 \
        || { tail -40 "$RUN/build.log"; fail "build"; }
fi

echo "==> stack"
compose up -d --wait > "$RUN/compose.log" 2>&1 || { cat "$RUN/compose.log"; fail "compose up"; }
for url in "$ORDERING/actuator/health/readiness" "$FORGE/actuator/health/readiness"; do
    for _ in $(seq 1 60); do [ "$(code "$url")" = "200" ] && break; sleep 2; done
    [ "$(code "$url")" = "200" ] || fail "$url not ready"
done
ok "ordering, forge-service, supplier, Kafka, Apicurio, two databases up"

echo "==> registry"
rule=$(curl -sf "$REGISTRY/apis/registry/v3/groups/default/artifacts/cdc.forge.requests-value/rules/COMPATIBILITY")
echo "$rule" | grep -q BACKWARD || fail "cdc.forge.requests-value has no BACKWARD rule: $rule"
ok "schemas registered by schema-init, rule BACKWARD"

echo "==> a quoted custom order"
id=$(place '{"customerId":"e2e","custom":true,"material":"IRON-01","quantity":5}')
[ "$(order "$id" | json status)" = "ACCEPTED" ] || fail "IRON-01 x5 not ACCEPTED: $(order "$id")"
[ "$(order "$id" | json priceCents)" = "6000" ] || fail "price: $(order "$id")"
[ "$(order "$id" | json leadTimeDays)" = "3" ] || fail "lead time: $(order "$id")"
ok "IRON-01 x5 accepted at 6000 cents, 3 days (quote over gRPC)"

wo=""
for _ in $(seq 1 30); do
    wo=$(curl -sf "$FORGE/api/work-orders" | python3 -c 'import json,sys
print(next((w["id"] for w in json.load(sys.stdin) if w["orderId"]==sys.argv[1]),""))' "$id")
    [ -n "$wo" ] && break
    sleep 1
done
[ -n "$wo" ] || fail "no work order in forge-service for $id (Avro request lost?)"
[ "$(code -X POST "$FORGE/api/work-orders/$wo/start")" = "200" ] || fail "start $wo"
[ "$(code -X POST "$FORGE/api/work-orders/$wo/complete")" = "200" ] || fail "complete $wo"
wait_status "$id" READY 30
ok "work order $wo done, order READY (Avro ForgeEvent back)"

echo "==> refusals"
id=$(place '{"customerId":"e2e","custom":true,"material":"MITHRIL-01","quantity":1}')
wait_status "$id" REJECTED 30
[ "$(order "$id" | json reason)" = "material not deliverable: MITHRIL-01" ] || fail "MITHRIL reason: $(order "$id")"
ok "MITHRIL-01 quoted, then REJECTED by Forge: material not deliverable"
id=$(place '{"customerId":"e2e","custom":true,"material":"FOO","quantity":1}')
[ "$(order "$id" | json status)" = "REJECTED" ] || fail "FOO not rejected: $(order "$id")"
[ "$(order "$id" | json reason)" = "unknown material: FOO" ] || fail "FOO reason: $(order "$id")"
ok "FOO REJECTED at once: unknown material"
id=$(place '{"customerId":"e2e","sku":"HAMMER-01"}')
[ "$(order "$id" | json status)" = "READY" ] || fail "HAMMER-01 not READY: $(order "$id")"
ok "stock order HAMMER-01 READY without a quote"

echo "==> without Forge"
compose stop forge-service > /dev/null 2>&1
id=$(place '{"customerId":"e2e","custom":true,"material":"IRON-01","quantity":1}')
[ "$(order "$id" | json status)" = "REJECTED" ] || fail "order without Forge not rejected: $(order "$id")"
[ "$(order "$id" | json reason)" = "quote unavailable" ] || fail "reason without Forge: $(order "$id")"
ok "Forge stopped: REJECTED at once, quote unavailable"

echo "E2E OK"
