#!/usr/bin/env bash
# Self-test of reconcile.sh without a cluster: orders are read from a fixture dump instead of Ordering's database,
# reservations from fixture dumps instead of the databases.
#   bash 12-scaling-operations/scripts/test/reconcile-fixture.sh
set -uo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
RECONCILE="$HERE/../reconcile.sh"
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
failures=0
PAYMENT_DUMP_OVERRIDE=""

order() { # id status reason -> one row of the purchase_order dump
    echo "$1|$2|${3:-}" >> "$WORK/orders.txt"
}
run() { # name expected-exit csv [grep pattern on output]
    local out code
    out=$(ORDERS_DUMP_CMD="cat $WORK/orders.txt" WAIT_S=2 POLL_S=1 EFFECTS_WAIT_S=2 \
        PAYMENT_DUMP_CMD="${PAYMENT_DUMP_OVERRIDE:-cat $WORK/payment.txt}" INVENTORY_DUMP_CMD="cat $WORK/inventory.txt" \
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

: > "$WORK/orders.txt"
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

# A compensation still in flight when its order settles: the refund lands a moment later (found at 50/s).
# The first payment dump still shows a7 RESERVED, the next ones REFUNDED.
printf 'a1|RESERVED\na6|RESERVED\na7|REFUNDED\n' > "$WORK/payment-later.txt"
printf 'a1|RESERVED\na6|RESERVED\na7|RESERVED\n' > "$WORK/payment.txt"
cat > "$WORK/payment-dump.sh" <<EOF
if [ -e "$WORK/payment-read" ]; then cat "$WORK/payment-later.txt"; else touch "$WORK/payment-read"; cat "$WORK/payment.txt"; fi
EOF
PAYMENT_DUMP_OVERRIDE="bash $WORK/payment-dump.sh"
run "a compensation landing after the order settled passes" 0 "$WORK/good.csv" "payment: reservations match"
PAYMENT_DUMP_OVERRIDE=""
printf 'a7|RESERVED\n' > "$WORK/payment.txt"
run "a compensation that never lands fails and names the order" 1 "$WORK/comp.csv" "kept a7"
printf 'a1|RESERVED\na6|RESERVED\na7|REFUNDED\n' > "$WORK/payment.txt"

# A peak run accepts thousands of orders: reconcile must not spend a process per order (minutes under Git Bash)
: > "$WORK/orders.txt"; : > "$WORK/big.csv"; : > "$WORK/payment.txt"; : > "$WORK/inventory.txt"
for i in $(seq 1 8000); do echo "b$i|CONFIRMED|"; done > "$WORK/orders.txt"
for i in $(seq 1 8000); do echo "b$i,choreography,CONFIRMED"; done > "$WORK/big.csv"
for i in $(seq 1 8000); do echo "b$i|RESERVED"; done | tee "$WORK/inventory.txt" > "$WORK/payment.txt"
started=$(date +%s)
run "8000 orders reconcile" 0 "$WORK/big.csv" "accepted=8000 confirmed=8000"
took=$(($(date +%s) - started))
if [ "$took" -gt 15 ]; then
    echo "FAIL  8000 orders took ${took}s (want <= 15s)"
    failures=$((failures + 1))
else
    echo "ok    8000 orders within ${took}s"
fi

[ "$failures" -eq 0 ] && echo "reconcile self-test OK" || { echo "reconcile self-test: $failures failure(s)"; exit 1; }
