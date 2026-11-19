#!/usr/bin/env bash
# Self-test of reconcile.sh without a cluster: orders are read from fixture files instead of the Ordering API,
# reservations from fixture dumps instead of the databases.
#   bash 12-scaling-operations/scripts/test/reconcile-fixture.sh
set -uo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
RECONCILE="$HERE/../reconcile.sh"
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
failures=0

order() { # id status reason
    local reason=""
    [ -n "${3:-}" ] && reason=",\"reason\":\"$3\""
    echo "{\"id\":\"$1\",\"customerId\":\"x\",\"saga\":\"choreography\",\"status\":\"$2\"$reason,\"history\":[]}" > "$WORK/orders/$1.json"
}
run() { # name expected-exit csv [grep pattern on output]
    local out code
    out=$(ORDER_CMD="cat $WORK/orders/%s.json" WAIT_S=2 POLL_S=1 \
        PAYMENT_DUMP_CMD="cat $WORK/payment.txt" INVENTORY_DUMP_CMD="cat $WORK/inventory.txt" \
        PAYMENT_INVARIANT_CMD="cat $WORK/payment-drift.txt" INVENTORY_INVARIANT_CMD="cat $WORK/inventory-drift.txt" \
        bash "$RECONCILE" "$3" 2>&1)
    code=$?
    if [ "$code" -ne "$2" ] || { [ -n "${4:-}" ] && ! echo "$out" | grep -q -- "$4"; }; then
        echo "FAIL  $1 (exit $code, want $2${4:+, want output '$4'})"
        echo "$out" | sed 's/^/      /'
        failures=$((failures + 1))
    else
        echo "ok    $1"
    fi
}

mkdir -p "$WORK/orders"
# Drift rows: accounts / SKUs whose reserved total differs from their RESERVED reservations (none = consistent)
: > "$WORK/payment-drift.txt"
: > "$WORK/inventory-drift.txt"
order a1 CONFIRMED
order a2 REJECTED "credit limit exceeded"
order a3 PENDING
order a5 REJECTED "timeout at step payment"
order a6 CONFIRMED
order a7 REJECTED "not enough stock"
# Reservations: a1 confirmed and reserved in both; a7 compensated (refunded, never stock-reserved)
printf 'a1|RESERVED\na6|RESERVED\na7|REFUNDED\n' > "$WORK/payment.txt"
printf 'a1|RESERVED\na6|RESERVED\n' > "$WORK/inventory.txt"

printf 'a1,choreography,CONFIRMED\na2,choreography,REJECTED:credit limit exceeded\na7,orchestration,REJECTED:not enough stock\n' > "$WORK/good.csv"
run "all settled as expected" 0 "$WORK/good.csv" "accepted=3 confirmed=1 rejected=2 pending=0 missing=0 unexpected=0 timeouts=0"

printf 'a1,choreography,CONFIRMED\na3,choreography,CONFIRMED\na4,choreography,CONFIRMED\n' > "$WORK/lost.csv"
run "pending and missing orders fail" 1 "$WORK/lost.csv" "pending=1 missing=1"

printf 'a5,orchestration,CONFIRMED\n' > "$WORK/timeout.csv"
run "a timeout rejection fails" 1 "$WORK/timeout.csv" "timeouts=1"

printf 'a2,choreography,CONFIRMED\n' > "$WORK/unexpected.csv"
run "an outcome other than expected fails" 1 "$WORK/unexpected.csv" "unexpected=1"

# a6 confirmed but its stock reservation is missing: the saga lost an effect
printf 'a6,choreography,CONFIRMED\n' > "$WORK/effects.csv"
printf 'a1|RESERVED\n' > "$WORK/inventory.txt"
run "a confirmed order without a stock reservation fails" 1 "$WORK/effects.csv" "inventory: 1 confirmed order"
printf 'a1|RESERVED\na6|RESERVED\n' > "$WORK/inventory.txt"

# a7 rejected but its payment still reserved: compensation missing
printf 'a7|RESERVED\n' > "$WORK/payment.txt"
printf 'a7,orchestration,REJECTED:not enough stock\n' > "$WORK/comp.csv"
run "a rejected order still holding money fails" 1 "$WORK/comp.csv" "payment: 1 rejected order"

# orders.csv written on Windows (CRLF): the expectation must still compare equal
printf 'a1|RESERVED\na6|RESERVED\na7|REFUNDED\n' > "$WORK/payment.txt"
printf 'a1,choreography,CONFIRMED\r\na7,orchestration,REJECTED:not enough stock\r\n' > "$WORK/crlf.csv"
run "a CRLF orders.csv compares like LF" 0 "$WORK/crlf.csv" "unexpected=0"

# A reservation applied twice to the stock counter (idempotency bug): rows exist once, the counter says twice
printf 'HAMMER-01|52|26\n' > "$WORK/inventory-drift.txt"
run "a stock counter out of line with its reservations fails" 1 "$WORK/good.csv" "inventory: 1 counter(s) out of line"
: > "$WORK/inventory-drift.txt"
printf 'peak-1-3|240.00|120.00\n' > "$WORK/payment-drift.txt"
run "a credit counter out of line with its reservations fails" 1 "$WORK/good.csv" "payment: 1 counter(s) out of line"
: > "$WORK/payment-drift.txt"

[ "$failures" -eq 0 ] && echo "reconcile self-test OK" || { echo "reconcile self-test: $failures failure(s)"; exit 1; }
