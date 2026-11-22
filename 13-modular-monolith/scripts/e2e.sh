#!/usr/bin/env bash
# The Strangler Fig end to end in Docker Compose (CI): custom orders flow and a smith bot works all the time while
# the monolith goes before -> cutover -> drain -> decommission; then the reconciliation: every accepted custom order
# has exactly one finished work order (in the monolith or in forge-service), none lost, none twice.
#   13-modular-monolith/scripts/e2e.sh     (SKIP_BUILD=true reuses images, KEEP_STACK=true keeps the stack up)
# Env: RATE (2 orders/s), BEFORE_S (30), AFTER_S (30). Needs JDK 25, Docker, curl.
set -euo pipefail

cd "$(dirname "$0")/.."   # 13-modular-monolith
PROJECT=hephaestus-13
RATE="${RATE:-2}"
BEFORE_S="${BEFORE_S:-30}"
AFTER_S="${AFTER_S:-30}"
RUN="target/e2e-$(date +%Y%m%d-%H%M%S)"
MONOLITH=http://localhost:18080
SERVICE=http://localhost:18081
export MONOLITH_URL=$MONOLITH COMPOSE_FILE="$PWD/compose.yaml"

ok() { echo "ok    $*"; }
fail() {
    echo "FAIL  $*" >&2
    compose logs --tail=60 modular forge-service >&2 || true
    exit 1
}
compose() { docker compose -f compose.yaml -p "$PROJECT" "$@"; }
code() { curl -s -o /dev/null -w '%{http_code}' "$@"; }
BOT=""
GEN=""
cleanup() {
    [ -n "$BOT" ] && kill "$BOT" 2>/dev/null || true
    [ -n "$GEN" ] && kill "$GEN" 2>/dev/null || true
    if [ "${KEEP_STACK:-false}" != "true" ]; then
        compose down -v >/dev/null 2>&1 || true
    fi
}
trap cleanup EXIT
mkdir -p "$RUN"

if [ "${SKIP_BUILD:-false}" != "true" ]; then
    echo "==> build (images, traffic jar)"
    ../mvnw -B -ntp -f pom.xml package jib:dockerBuild -DskipTests -Dspotless.check.skip=true > "$RUN/build.log" 2>&1 \
        || { tail -40 "$RUN/build.log"; fail "build"; }
fi

echo "==> stack"
compose up -d --wait > "$RUN/compose.log" 2>&1 || { cat "$RUN/compose.log"; fail "compose up"; }
for url in "$MONOLITH/actuator/health/readiness" "$SERVICE/actuator/health/readiness"; do
    for _ in $(seq 1 60); do [ "$(code "$url")" = "200" ] && break; sleep 2; done
    [ "$(code "$url")" = "200" ] || fail "$url not ready"
done
ok "monolith, forge-service, supplier, Kafka, two databases up"

echo "==> before: work orders in the monolith"
scripts/cutover.sh before > /dev/null
MONOLITH_URL=$MONOLITH SERVICE_URL=$SERVICE INTERVAL_MS=500 java -jar traffic/target/traffic.jar bot \
    > "$RUN/bot.log" 2>&1 &
BOT=$!
ORDERS_URL=$MONOLITH RATE=$RATE SECONDS=$((BEFORE_S + AFTER_S)) OUT="$RUN/orders.csv" \
    java -jar traffic/target/traffic.jar generate > "$RUN/generator.log" 2>&1 &
GEN=$!
sleep "$BEFORE_S"

echo "==> cutover: new work orders to forge-service, while orders keep coming"
scripts/cutover.sh cutover > /dev/null || fail "cutover"
ok "cut over at $(date +%T)"
wait "$GEN" || fail "generator: $(tail -5 "$RUN/generator.log")"
GEN=""
ok "generator: $(tail -1 "$RUN/generator.log")"

echo "==> drain: the monolith finishes its WO- work orders"
DRAIN_TIMEOUT=180 scripts/cutover.sh drain || fail "drain"

echo "==> decommission"
scripts/cutover.sh decommission > /dev/null || fail "decommission"
[ "$(code "$MONOLITH/api/work-orders")" = "410" ] || fail "the monolith's smith API is not gone after decommission"
ok "local forge decommissioned (smith API 410)"

echo "==> reconciliation"
WAIT_S=120 scripts/reconcile.sh "$RUN/orders.csv" | tee "$RUN/reconcile.txt" || fail "reconciliation"
local_n=$(compose exec -T postgres-monolith psql -U workshop -d workshop -Atc "select count(*) from forge.work_order")
service_n=$(compose exec -T postgres-forge psql -U forge -d forge -Atc "select count(*) from work_order")
ok "work orders: monolith $(echo "$local_n" | tr -d '\r'), forge-service $(echo "$service_n" | tr -d '\r')"
[ "$(echo "$local_n" | tr -d '\r')" -gt 0 ] && [ "$(echo "$service_n" | tr -d '\r')" -gt 0 ] \
    || fail "both sides must have served work orders (before and after the cutover)"
echo "E2E OK: run files in 13-modular-monolith/$RUN"
