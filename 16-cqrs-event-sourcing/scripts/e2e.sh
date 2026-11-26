#!/usr/bin/env bash
# 16 end to end in Docker Compose (CI): a custom order is quoted by Forge over gRPC, accepted with price and lead
# time, travels as Avro through Apicurio, and becomes READY when smith anna completes it; Forge's state is its event
# journal: the projections catch up to its head, smith-load counts anna, the stock ledger equals a fold of the
# journal, and stock "at" a moment before the first order is the seeded 20; refusals carry their reason; without
# Forge the order is rejected at once ("quote unavailable").
#   16-cqrs-event-sourcing/scripts/e2e.sh     (SKIP_BUILD=true reuses images, KEEP_STACK=true keeps the stack up)
# Needs JDK 25, Docker, curl, python3.
set -euo pipefail

cd "$(dirname "$0")/.."   # 16-cqrs-event-sourcing
PROJECT=hephaestus-16
CUSTOM_IDS=()
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
# Git Bash: MSYS_NO_PATHCONV keeps container paths (/opt/kafka/bin) from becoming C:/... (only here: curl
# on the host needs /dev/null translated)
compose() { MSYS_NO_PATHCONV=1 docker compose -f compose.yaml -p "$PROJECT" "$@"; }
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
rule=$(curl -sf "$REGISTRY/apis/registry/v3/groups/default/artifacts/es.forge.requests-value/rules/COMPATIBILITY")
echo "$rule" | grep -q BACKWARD || fail "es.forge.requests-value has no BACKWARD rule: $rule"
ok "schemas registered by schema-init, rule BACKWARD"

echo "==> a quoted custom order"
before_orders=$(date -u +%Y-%m-%dT%H:%M:%SZ)
sleep 1
id=$(place '{"customerId":"e2e","custom":true,"material":"IRON-01","quantity":5}')
CUSTOM_IDS+=("$id")
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
[ "$(code -X POST "$FORGE/api/work-orders/$wo/start")" = "400" ] || fail "start $wo without smith not 400"
[ "$(code -X POST "$FORGE/api/work-orders/$wo/start?smith=anna")" = "200" ] || fail "start $wo"
[ "$(code -X POST "$FORGE/api/work-orders/$wo/start?smith=brokk")" = "409" ] || fail "second start $wo not 409"
[ "$(code -X POST "$FORGE/api/work-orders/$wo/complete")" = "200" ] || fail "complete $wo"
wait_status "$id" READY 30
ok "work order $wo done by anna (second smith: 409), order READY (Avro ForgeEvent back from the journal)"

echo "==> projections and time travel"
caught_up=""
for _ in $(seq 1 30); do
    caught_up=$(curl -sf "$FORGE/admin/projections" | python3 -c 'import json,sys
s=json.load(sys.stdin)
names={p["name"] for p in s}
ok=all(p["lag"]==0 and p["lastError"]=="" for p in s) and {"workshop-board","smith-load","stock-ledger"} <= names
print("yes" if ok else "")')
    [ -n "$caught_up" ] && break
    sleep 1
done
[ -n "$caught_up" ] || fail "projections not at the journal head: $(curl -s "$FORGE/admin/projections")"
ok "workshop-board, smith-load, stock-ledger at the journal head, no errors"
done_by_anna=$(compose exec -T postgres-forge psql -U forge -d forge -Atc \
    "select done from read.smith_load where smith = 'anna'" | tr -d '\r')
[ "$done_by_anna" = "1" ] || fail "smith-load for anna: '$done_by_anna' (expected done=1)"
ok "smith-load: anna has 1 done"
ledger=$(curl -sf "$FORGE/api/stock/IRON-01" | json onHand)
folded=$(curl -sf "$FORGE/api/stock/IRON-01?at=$(date -u -d '+1 minute' +%Y-%m-%dT%H:%M:%SZ 2> /dev/null \
    || python3 -c 'import datetime;print((datetime.datetime.now(datetime.timezone.utc)+datetime.timedelta(minutes=1)).strftime("%Y-%m-%dT%H:%M:%SZ"))')" | json onHand)
[ "$ledger" = "$folded" ] || fail "IRON-01: ledger $ledger, journal fold $folded"
seeded=$(curl -sf "$FORGE/api/stock/IRON-01?at=$before_orders" | json onHand)
[ "$seeded" = "20" ] || fail "IRON-01 at $before_orders: $seeded (expected the seeded 20)"
ok "IRON-01: ledger $ledger = journal fold; before the first order: 20"

echo "==> refusals"
id=$(place '{"customerId":"e2e","custom":true,"material":"MITHRIL-01","quantity":1}')
CUSTOM_IDS+=("$id")
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

echo "==> one record per order on es.forge.requests"
sleep 15
# Joining the consumer group can take seconds: a generous idle timeout, keys only (first field), no CR
compose exec -T kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
    --topic es.forge.requests --from-beginning --timeout-ms 30000 \
    --property print.key=true --property print.value=false 2> "$RUN/keys.err" \
    | tr -d '\r' | cut -f1 > "$RUN/keys.txt" || true
# keys are order ids: anything else (a docker error, say) means the read itself failed
grep -qEx '[0-9a-f-]{36}' "$RUN/keys.txt" \
    || fail "read no order keys from es.forge.requests: $(head -c 300 "$RUN/keys.txt") $(tail -3 "$RUN/keys.err")"
for cid in "${CUSTOM_IDS[@]}"; do
    n=$(grep -cFx "$cid" "$RUN/keys.txt" || true)
    [ "$n" = "1" ] || fail "order $cid has $n records on es.forge.requests (expected exactly 1; $RUN/keys.txt)"
done
ok "each quoted order: exactly one request record"

echo "==> without Forge"
compose stop forge-service > /dev/null 2>&1
id=$(place '{"customerId":"e2e","custom":true,"material":"IRON-01","quantity":1}')
[ "$(order "$id" | json status)" = "REJECTED" ] || fail "order without Forge not rejected: $(order "$id")"
[ "$(order "$id" | json reason)" = "quote unavailable" ] || fail "reason without Forge: $(order "$id")"
ok "Forge stopped: REJECTED at once, quote unavailable"

echo "E2E OK"
