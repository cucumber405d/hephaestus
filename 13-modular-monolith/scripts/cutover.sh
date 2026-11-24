#!/usr/bin/env bash
# The Strangler Fig steps against the monolith's admin API (ADR-0009, variant A):
#   13-modular-monolith/scripts/cutover.sh <state|before|cutover|drain|decommission|rollback>
#   before        new work orders go to the local forge (the start, and what rollback returns to)
#   cutover       new work orders go to forge-service (the monolith publishes them to Kafka)
#   drain         wait until the monolith has no open WO- work order (DRAIN_TIMEOUT, 300 s)
#   decommission  switch the local forge off for good (refused while anything is open)
#   rollback      same as before, after a cutover: FW- orders finish in the service
# Env: MONOLITH_URL (http://localhost:18080), HOST_HEADER (optional, to go through the Gateway by IP).
set -euo pipefail

STEP="${1:?usage: cutover.sh <state|before|cutover|drain|decommission|rollback>}"
MONOLITH_URL="${MONOLITH_URL:-http://localhost:18080}"
DRAIN_TIMEOUT="${DRAIN_TIMEOUT:-300}"
HEADERS=()
[ -n "${HOST_HEADER:-}" ] && HEADERS=(-H "Host: $HOST_HEADER")

state() { curl -fsS "${HEADERS[@]}" "$MONOLITH_URL/admin/forge"; }
open_local() { state | grep -o '"openLocalWorkOrders":[0-9]*' | cut -d: -f2; }
post() { # path body -> prints the answer, fails on a non-2xx with its body
    local out code
    out=$(curl -sS "${HEADERS[@]}" -o - -w '\n%{http_code}' -X POST -H 'Content-Type: application/json' \
        ${2:+-d "$2"} "$MONOLITH_URL$1")
    code=$(echo "$out" | tail -1)
    echo "$out" | sed '$d'
    [ "${code:0:1}" = "2" ] || { echo "cutover: $1 -> HTTP $code" >&2; return 1; }
}

case "$STEP" in
    state) state; echo ;;
    before | rollback) post /admin/forge/cutover '{"remote":false}'; echo ;;
    cutover) post /admin/forge/cutover '{"remote":true}'; echo ;;
    drain)
        deadline=$(($(date +%s) + DRAIN_TIMEOUT))
        while true; do
            n=$(open_local | tr -d '\r')
            echo "    open local work orders: $n"
            [ "$n" = "0" ] && break
            [ "$(date +%s)" -ge "$deadline" ] && { echo "cutover: drain timed out with $n open" >&2; exit 1; }
            sleep 5
        done
        ;;
    decommission) post /admin/forge/decommission; echo ;;
    *) echo "unknown step: $STEP" >&2; exit 2 ;;
esac
