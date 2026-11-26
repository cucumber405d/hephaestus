#!/usr/bin/env bash
# reconcile.sh on canned query results (RECONCILE_FAKE_DIR: ordering.txt, forge.txt) — no Docker needed.
set -euo pipefail

SCRIPTS="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
A=11111111-1111-4111-8111-111111111111
B=22222222-2222-4222-8222-222222222222
C=33333333-3333-4333-8333-333333333333
failures=0

# case <name> <expected exit> <expected missing> <orders> <ordering rows> <forge rows>
case_() {
    local name="$1" want_exit="$2" want_missing="$3"
    printf '%s\n' $4 > "$TMP/orders.txt"
    printf '%s\n' $5 > "$TMP/ordering.txt"
    printf '%s\n' $6 > "$TMP/forge.txt"
    set +e
    out=$(RECONCILE_FAKE_DIR="$TMP" "$SCRIPTS/reconcile.sh" "$TMP/orders.txt")
    code=$?
    set -e
    if [[ $code -eq $want_exit && "$out" == *"missing=$want_missing"* ]]; then
        echo "ok    $name"
    else
        echo "FAIL  $name: exit $code, output '$out' (expected exit $want_exit, missing=$want_missing)" >&2
        failures=$((failures + 1))
    fi
}

case_ "every accepted order has its work order" 0 0 "$A $B" "$A $B" "$A $B"
case_ "one work order missing" 1 1 "$A $B" "$A $B" "$A"
case_ "an order without a quote (rejected) is not counted" 0 0 "$A $C" "$A" "$A"
case_ "nothing placed" 0 0 "" "" ""

[[ $failures -eq 0 ]] && echo "reconcile fixture OK"
exit $failures
