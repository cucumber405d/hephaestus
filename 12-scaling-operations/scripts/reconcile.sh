#!/usr/bin/env bash
# The "no accepted order lost" check of ADR-0001, after a peak run:
#   1. every order Gatling got a 202 for reaches CONFIRMED or REJECTED within WAIT_S seconds (none PENDING, none missing),
#   2. it ends as intended (orders.csv: expected outcome), and no order is rejected by a step timeout,
#   3. the effects match: a confirmed order holds its money (Payment) and stock (Inventory) reservation, a rejected
#      one holds neither (its compensations ran), and the counters (account.reserved, stock_item.reserved) equal the
#      sum of their reservations — a reservation applied twice shows up there.
#   12-scaling-operations/scripts/reconcile.sh <orders.csv>
# Env: ORDERS_URL (http://orders.localtest.me), WAIT_S (300), POLL_S (10), NAMESPACE (hephaestus-12);
#      ORDER_CMD / PAYMENT_DUMP_CMD / INVENTORY_DUMP_CMD replace the API and database reads (self-test).
set -euo pipefail

CSV="${1:?usage: reconcile.sh <orders.csv>}"
ORDERS_URL="${ORDERS_URL:-http://orders.localtest.me}"
WAIT_S="${WAIT_S:-300}"
POLL_S="${POLL_S:-10}"
NAMESPACE="${NAMESPACE:-hephaestus-12}"
ORDER_CMD="${ORDER_CMD:-curl -fs --max-time 10 --resolve ${ORDERS_URL#http://}:80:${GATEWAY:-127.0.0.1} $ORDERS_URL/api/orders/%s}"
# order_id|status of every reservation, straight from the participants' databases (CNPG pod <db>-1)
PAYMENT_DUMP_CMD="${PAYMENT_DUMP_CMD:-kubectl -n $NAMESPACE exec payment-db-1 -c postgres -- psql -U postgres -d payment -At -c 'select order_id, status from payment_reservation'}"
INVENTORY_DUMP_CMD="${INVENTORY_DUMP_CMD:-kubectl -n $NAMESPACE exec inventory-db-1 -c postgres -- psql -U postgres -d inventory -At -c 'select order_id, status from stock_reservation'}"
# Double effects (ADR-0001 item 2): a counter that moved twice for one reservation. Rows: key|counter|sum of RESERVED
PAYMENT_INVARIANT_CMD="${PAYMENT_INVARIANT_CMD:-kubectl -n $NAMESPACE exec payment-db-1 -c postgres -- psql -U postgres -d payment -At -c \"select a.customer_id, a.reserved, coalesce(r.s, 0) from account a left join (select customer_id, sum(amount) s from payment_reservation where status = 'RESERVED' group by customer_id) r using (customer_id) where a.reserved <> coalesce(r.s, 0)\"}"
INVENTORY_INVARIANT_CMD="${INVENTORY_INVARIANT_CMD:-kubectl -n $NAMESPACE exec inventory-db-1 -c postgres -- psql -U postgres -d inventory -At -c \"select i.sku, i.reserved, coalesce(x.q, 0) from stock_item i left join (select l.sku, sum(l.quantity) q from stock_reservation_line l join stock_reservation r using (order_id) where r.status = 'RESERVED' group by l.sku) x using (sku) where i.reserved <> coalesce(x.q, 0)\"}"

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
# A log written on Windows ends its lines with \r\n: drop the \r, or no expectation would compare equal
tr -d '\r' < "$CSV" > "$WORK/orders.csv"
CSV="$WORK/orders.csv"

# Reads one order; prints "STATUS|reason" or "MISSING"
fetch() {
    local json
    # shellcheck disable=SC2059
    if ! json=$(eval "$(printf "$ORDER_CMD" "$1")" 2>/dev/null) || [ -z "$json" ]; then
        echo "MISSING"
        return
    fi
    local status reason
    status=$(echo "$json" | sed -n 's/.*"status":"\([A-Z]*\)".*/\1/p')
    reason=$(echo "$json" | sed -n 's/.*"reason":"\([^"]*\)".*/\1/p')
    echo "${status:-MISSING}|$reason"
}

cut -d, -f1 "$CSV" | sort -u > "$WORK/todo"
accepted=$(wc -l < "$WORK/todo" | tr -d ' ')
: > "$WORK/settled"   # id|STATUS|reason
deadline=$(($(date +%s) + WAIT_S))

echo "==> waiting for $accepted accepted order(s) to settle (up to ${WAIT_S}s)"
while true; do
    : > "$WORK/still"
    while read -r id; do
        result=$(fetch "$id")
        case "$result" in
            CONFIRMED\|* | REJECTED\|*) echo "$id|$result" >> "$WORK/settled" ;;
            *) echo "$id|$result" >> "$WORK/still" ;;
        esac
    done < "$WORK/todo"
    cut -d'|' -f1 "$WORK/still" > "$WORK/todo"
    [ ! -s "$WORK/todo" ] && break
    [ "$(date +%s)" -ge "$deadline" ] && break
    echo "    $(wc -l < "$WORK/todo" | tr -d ' ') not settled yet"
    sleep "$POLL_S"
done

pending=$(grep -c '|PENDING|' "$WORK/still" || true)
missing=$(grep -c '|MISSING' "$WORK/still" || true)
confirmed=$(grep -c '|CONFIRMED|' "$WORK/settled" || true)
rejected=$(grep -c '|REJECTED|' "$WORK/settled" || true)
timeouts=$(grep -c '|REJECTED|timeout at step' "$WORK/settled" || true)

# Outcome against the expectation: CONFIRMED, or REJECTED:<reason>
unexpected=0
while IFS=, read -r id _style expected; do
    line=$(grep "^$id|" "$WORK/settled" || true)
    [ -z "$line" ] && continue
    actual=$(echo "$line" | cut -d'|' -f2)
    reason=$(echo "$line" | cut -d'|' -f3)
    [ "$actual" = "REJECTED" ] && actual="REJECTED:$reason"
    if [ "$actual" != "$expected" ]; then
        unexpected=$((unexpected + 1))
        echo "    unexpected: $id expected $expected, got $actual" >&2
    fi
done < "$CSV"

echo "accepted=$accepted confirmed=$confirmed rejected=$rejected pending=$pending missing=$missing unexpected=$unexpected timeouts=$timeouts"
grep '|PENDING|\||MISSING' "$WORK/still" | head -10 | sed 's/^/    not settled: /' >&2 || true

# Effects in the participants' databases
effects_failed=0
check_effects() { # name dump-command
    local name=$1 dump=$2 lost=0 kept=0
    if ! eval "$dump" > "$WORK/$name.txt" 2>"$WORK/$name.err"; then
        echo "$name: cannot read reservations ($(head -1 "$WORK/$name.err"))"
        effects_failed=1
        return
    fi
    # (grep finds nothing -> exit 1; not an error here)
    { grep '|CONFIRMED|' "$WORK/settled" || true; } | cut -d'|' -f1 | while read -r id; do
        grep -q "^$id|RESERVED$" "$WORK/$name.txt" || echo "lost $id"
    done > "$WORK/$name.lost"
    { grep '|REJECTED|' "$WORK/settled" || true; } | cut -d'|' -f1 | while read -r id; do
        if grep -q "^$id|RESERVED$" "$WORK/$name.txt"; then echo "kept $id"; fi
    done > "$WORK/$name.kept"
    lost=$(wc -l < "$WORK/$name.lost" | tr -d ' ')
    kept=$(wc -l < "$WORK/$name.kept" | tr -d ' ')
    if [ "$lost" -gt 0 ]; then
        echo "$name: $lost confirmed order(s) without a reservation"
        effects_failed=1
    fi
    if [ "$kept" -gt 0 ]; then
        echo "$name: $kept rejected order(s) still holding a reservation (compensation missing)"
        effects_failed=1
    fi
    [ "$lost" -eq 0 ] && [ "$kept" -eq 0 ] && echo "$name: reservations match ($confirmed confirmed, $rejected rejected)"
    return 0
}
check_effects payment "$PAYMENT_DUMP_CMD"
check_effects inventory "$INVENTORY_DUMP_CMD"

# The counters (account.reserved, stock_item.reserved) must equal the sum of their RESERVED reservations
check_counters() { # name invariant-command
    local name=$1 drift
    if ! drift=$(eval "$2" 2>"$WORK/$name-inv.err"); then
        echo "$name: cannot read counters ($(head -1 "$WORK/$name-inv.err"))"
        effects_failed=1
        return 0
    fi
    if [ -n "$drift" ]; then
        echo "$name: $(echo "$drift" | grep -c .) counter(s) out of line with their reservations (key|counter|reservations):"
        echo "$drift" | head -5 | sed 's/^/    /'
        effects_failed=1
    else
        echo "$name: counters match their reservations"
    fi
}
check_counters payment "$PAYMENT_INVARIANT_CMD"
check_counters inventory "$INVENTORY_INVARIANT_CMD"

if [ "$pending" -eq 0 ] && [ "$missing" -eq 0 ] && [ "$unexpected" -eq 0 ] && [ "$timeouts" -eq 0 ] \
    && [ "$effects_failed" -eq 0 ]; then
    echo "RECONCILE OK: no accepted order lost"
else
    echo "RECONCILE FAILED"
    exit 1
fi
