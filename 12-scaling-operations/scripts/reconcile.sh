#!/usr/bin/env bash
# The "no accepted order lost" check of ADR-0001, after a peak run:
#   1. every order Gatling got a 202 for reaches CONFIRMED or REJECTED within WAIT_S seconds (none PENDING, none missing),
#   2. it ends as intended (orders.csv: expected outcome), and no order is rejected by a step timeout,
#   3. the effects match: a confirmed order holds its money (Payment) and stock (Inventory) reservation, a rejected
#      one holds neither (its compensations ran), and the counters (account.reserved, stock_item.reserved) equal the
#      sum of their reservations — a reservation applied twice shows up there.
#   12-scaling-operations/scripts/reconcile.sh <orders.csv>
# Env: WAIT_S (300), POLL_S (10), EFFECTS_WAIT_S (60), NAMESPACE (hephaestus-12);
#      ORDERS_DUMP_CMD / PAYMENT_DUMP_CMD / INVENTORY_DUMP_CMD / *_INVARIANT_CMD replace the database reads (self-test).
# Every read is one query per database and every comparison one awk pass: no process per order, so thousands of
# orders reconcile in seconds even under Git Bash on Windows.
set -euo pipefail

CSV="${1:?usage: reconcile.sh <orders.csv>}"
WAIT_S="${WAIT_S:-300}"
POLL_S="${POLL_S:-10}"
EFFECTS_WAIT_S="${EFFECTS_WAIT_S:-60}"
NAMESPACE="${NAMESPACE:-hephaestus-12}"
# id|status|reason of every order, straight from Ordering's database (CNPG pod ordering-db-1)
ORDERS_DUMP_CMD="${ORDERS_DUMP_CMD:-kubectl -n $NAMESPACE exec ordering-db-1 -c postgres -- psql -U postgres -d ordering -At -c \"select id, status, coalesce(reason, '') from purchase_order\"}"
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

cut -d, -f1 "$CSV" | sort -u > "$WORK/todo"
accepted=$(wc -l < "$WORK/todo" | tr -d ' ')
: > "$WORK/settled"   # id|STATUS|reason
deadline=$(($(date +%s) + WAIT_S))

echo "==> waiting for $accepted accepted order(s) to settle (up to ${WAIT_S}s)"
while true; do
    # kubectl exec on Windows may end lines with \r: strip it from every dump, as from the CSV
    if ! eval "$ORDERS_DUMP_CMD" 2> "$WORK/orders.err" | tr -d '\r' > "$WORK/orders.txt"; then
        echo "    cannot read orders ($(head -1 "$WORK/orders.err")), retrying" >&2
        : > "$WORK/orders.txt"   # nothing known this round: every order counts as missing
    fi
    : > "$WORK/still"
    # Join the open ids with the dump: CONFIRMED/REJECTED settle, anything else (or no row) stays open
    awk -F'|' -v settled="$WORK/settled" -v still="$WORK/still" '
        # FILENAME, not NR == FNR: with an empty dump NR == FNR holds for the second file and every id would vanish
        FILENAME == ARGV[1] { st[$1] = $2; rs[$1] = $3; next }
        !($1 in st) { print $1 "|MISSING|" >> still; next }
        st[$1] == "CONFIRMED" || st[$1] == "REJECTED" { print $1 "|" st[$1] "|" rs[$1] >> settled; next }
        { print $1 "|" st[$1] "|" rs[$1] >> still }
    ' "$WORK/orders.txt" "$WORK/todo"
    cut -d'|' -f1 "$WORK/still" > "$WORK/todo"
    [ ! -s "$WORK/todo" ] && break
    [ "$(date +%s)" -ge "$deadline" ] && break
    echo "    $(wc -l < "$WORK/todo" | tr -d ' ') not settled yet"
    sleep "$POLL_S"
done

pending=$(grep -c '|PENDING|' "$WORK/still" || true)
missing=$(grep -c '|MISSING|' "$WORK/still" || true)
confirmed=$(grep -c '|CONFIRMED|' "$WORK/settled" || true)
rejected=$(grep -c '|REJECTED|' "$WORK/settled" || true)
timeouts=$(grep -c '|REJECTED|timeout at step' "$WORK/settled" || true)
# Every order not CONFIRMED/REJECTED at the deadline, whatever its status (PENDING, MISSING or one not known here)
open=$(grep -c . "$WORK/still" || true)

# Outcome against the expectation (CSV: id,style,expected — expected is CONFIRMED or REJECTED:<reason>)
unexpected=$(awk -F'|' '
    FILENAME == ARGV[1] { got[$1] = ($2 == "REJECTED") ? "REJECTED:" $3 : $2; next }
    {
        id = $0; sub(/,.*/, "", id)
        want = $0; sub(/^[^,]*,[^,]*,/, "", want)
        if ((id in got) && got[id] != want) {
            n++
            print "    unexpected: " id " expected " want ", got " got[id] > "/dev/stderr"
        }
    }
    END { print n + 0 }
' "$WORK/settled" "$CSV")

echo "accepted=$accepted confirmed=$confirmed rejected=$rejected open=$open pending=$pending missing=$missing unexpected=$unexpected timeouts=$timeouts"
head -10 "$WORK/still" | sed 's/^/    not settled: /' >&2 || true

# Effects in the participants' databases
effects_failed=0
# Compensations reach the participants after the order settles (eventual consistency): a mismatch is re-read
# every POLL_S up to EFFECTS_WAIT_S before it counts.
check_effects() { # name dump-command
    local name=$1 dump=$2 lost=0 kept=0
    local until=$(($(date +%s) + EFFECTS_WAIT_S))
    while true; do
        if ! eval "$dump" 2>"$WORK/$name.err" | tr -d '\r' > "$WORK/$name.txt"; then
            # A failed read (API hiccup, database restart) is retried like a mismatch
            if [ "$(date +%s)" -ge "$until" ]; then
                echo "$name: cannot read reservations ($(head -1 "$WORK/$name.err"))"
                effects_failed=1
                return 0
            fi
            echo "    $name: cannot read reservations yet, retrying"
            sleep "$POLL_S"
            continue
        fi
        # Confirmed without a RESERVED row -> lost effect; rejected with one -> compensation missing
        : > "$WORK/$name.lost"
        : > "$WORK/$name.kept"
        awk -F'|' -v lost="$WORK/$name.lost" -v kept="$WORK/$name.kept" '
            FILENAME == ARGV[1] { if ($2 == "RESERVED") r[$1] = 1; next }
            $2 == "CONFIRMED" && !($1 in r) { print "lost " $1 >> lost }
            $2 == "REJECTED" && ($1 in r) { print "kept " $1 >> kept }
        ' "$WORK/$name.txt" "$WORK/settled"
        lost=$(wc -l < "$WORK/$name.lost" | tr -d ' ')
        kept=$(wc -l < "$WORK/$name.kept" | tr -d ' ')
        [ "$lost" -eq 0 ] && [ "$kept" -eq 0 ] && break
        [ "$(date +%s)" -ge "$until" ] && break
        echo "    $name: $((lost + kept)) reservation(s) not in line yet, re-reading"
        sleep "$POLL_S"
    done
    if [ "$lost" -gt 0 ]; then
        echo "$name: $lost confirmed order(s) without a reservation"
        head -5 "$WORK/$name.lost" | sed 's/^/    /'
        effects_failed=1
    fi
    if [ "$kept" -gt 0 ]; then
        echo "$name: $kept rejected order(s) still holding a reservation (compensation missing)"
        head -5 "$WORK/$name.kept" | sed 's/^/    /'
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

# The totals close the books: every accepted order is counted exactly once as confirmed or rejected
settled_ok=0
[ "$open" -eq 0 ] && [ $((confirmed + rejected)) -eq "$accepted" ] && settled_ok=1
if [ "$settled_ok" -eq 1 ] && [ "$unexpected" -eq 0 ] && [ "$timeouts" -eq 0 ] \
    && [ "$effects_failed" -eq 0 ]; then
    echo "RECONCILE OK: no accepted order lost"
else
    echo "RECONCILE FAILED"
    exit 1
fi
