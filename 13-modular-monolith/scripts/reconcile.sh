#!/usr/bin/env bash
# "No work order lost, none done twice" across the cutover (spec, stage 2):
#   every accepted custom order (orders.csv: orderId,material,expected) has exactly one work order in the monolith
#   OR in forge-service, finished with the expected outcome (DONE, or FAILED for MITHRIL-01), and the customer order
#   agrees (READY for DONE, REJECTED for FAILED). Waits up to WAIT_S for open work orders to finish and for customer
#   orders to follow their finished work orders (the outcome travels over Kafka). Orders in the database that the
#   CSV does not know (a POST that timed out after the server committed) are checked the same way ("unlisted").
#   13-modular-monolith/scripts/reconcile.sh <orders.csv>
# Env: WAIT_S (180), POLL_S (5), COMPOSE_FILE (13-modular-monolith/compose.yaml), COMPOSE_PROJECT (hephaestus-13);
#      ORDERS_DUMP_CMD / MONOLITH_WO_DUMP_CMD / SERVICE_WO_DUMP_CMD replace the database reads (kind, self-test).
set -euo pipefail

CSV="${1:?usage: reconcile.sh <orders.csv>}"
WAIT_S="${WAIT_S:-180}"
POLL_S="${POLL_S:-5}"
HERE="$(cd "$(dirname "$0")" && pwd)"
COMPOSE_FILE="${COMPOSE_FILE:-$HERE/../compose.yaml}"
PSQL_MONOLITH="docker compose -f $COMPOSE_FILE -p ${COMPOSE_PROJECT:-hephaestus-13} exec -T postgres-monolith psql -U workshop -d workshop -At -c"
PSQL_FORGE="docker compose -f $COMPOSE_FILE -p ${COMPOSE_PROJECT:-hephaestus-13} exec -T postgres-forge psql -U forge -d forge -At -c"
# id|status|reason of every custom customer order the generator placed (customer cutover-<n>)
ORDERS_DUMP_CMD="${ORDERS_DUMP_CMD:-$PSQL_MONOLITH \"select id, status, coalesce(reason, '') from ordering.customer_order where custom and customer_id like 'cutover-%'\"}"
# order_id|work order id|status, on each side
MONOLITH_WO_DUMP_CMD="${MONOLITH_WO_DUMP_CMD:-$PSQL_MONOLITH \"select order_id, id, status from forge.work_order\"}"
SERVICE_WO_DUMP_CMD="${SERVICE_WO_DUMP_CMD:-$PSQL_FORGE \"select order_id, id, status from work_order\"}"

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
tr -d '\r' < "$CSV" > "$WORK/orders.csv"

dump() { # command file -> 0 when read (an empty table is a valid, if alarming, answer)
    eval "$1" 2> "$WORK/$2.err" | tr -d '\r' > "$WORK/$2"
}

# One pass: per order, what both sides say. Prints the counters line; per-order findings go to findings.txt.
# $1 = 1 at the deadline: what is still lagging then is a failure.
evaluate() {
    awk -F'|' -v csv="$WORK/orders.csv" -v findings="$WORK/findings.txt" -v final="$1" '
        FILENAME == ARGV[1] { order[$1] = $2; reason[$1] = $3; next }
        FILENAME == ARGV[2] || FILENAME == ARGV[3] {
            side = (FILENAME == ARGV[2]) ? "monolith" : "service"
            n[$1]++; wo[$1] = wo[$1] " " side ":" $2 ":" $3; st[$1] = $3; next
        }
        END {
            printf "" > findings
            while ((getline line < csv) > 0) {
                split(line, f, ",")
                id = f[1]; want = f[3]; accepted++; listed[id] = 1
                if (!(id in n)) { missing++; print "missing " id > findings; continue }
                if (n[id] > 1) { double++; print "double " id wo[id] > findings; continue }
                if (st[id] == "PLANNED" || st[id] == "IN_PROGRESS") { open++; print "open " id wo[id] > findings; continue }
                expect_order = (want == "DONE") ? "READY" : "REJECTED"
                if (st[id] == want && order[id] == "ACCEPTED" && !final) {
                    lagging++; print "lagging " id wo[id] > findings; continue
                }
                if (st[id] != want || order[id] != expect_order) {
                    unexpected++
                    print "unexpected " id " want " want "/" expect_order ", got" wo[id] " order " order[id] " " reason[id] > findings
                    continue
                }
                if (want == "DONE") done++; else failed++
            }
            # Orders the CSV does not know: exactly one finished work order, the customer order following it
            for (id in order) {
                if (id in listed) continue
                consistent = (n[id] == 1) && ((st[id] == "DONE" && order[id] == "READY") \
                    || (st[id] == "FAILED" && order[id] == "REJECTED"))
                if (consistent) continue
                if (!final) { lagging++; print "lagging (unlisted) " id wo[id] " order " order[id] > findings; continue }
                unlisted++; print "unlisted " id wo[id] " order " order[id] > findings
            }
            printf "accepted=%d done=%d failed=%d open=%d missing=%d double=%d unexpected=%d lagging=%d unlisted=%d\n",
                accepted, done, failed, open, missing, double, unexpected, lagging, unlisted
        }' "$WORK/orders.dump" "$WORK/monolith.dump" "$WORK/service.dump"
}

deadline=$(($(date +%s) + WAIT_S))
echo "==> reconciling $(grep -c . "$WORK/orders.csv" || true) accepted custom order(s) (up to ${WAIT_S}s)"
while true; do
    ok=1
    dump "$ORDERS_DUMP_CMD" orders.dump || { ok=0; echo "    cannot read customer orders ($(head -1 "$WORK/orders.dump.err"))" >&2; }
    dump "$MONOLITH_WO_DUMP_CMD" monolith.dump || { ok=0; echo "    cannot read the monolith's work orders" >&2; }
    dump "$SERVICE_WO_DUMP_CMD" service.dump || { ok=0; echo "    cannot read the service's work orders" >&2; }
    [ "$ok" -eq 1 ] || { : > "$WORK/orders.dump"; : > "$WORK/monolith.dump"; : > "$WORK/service.dump"; }
    line=$(evaluate 0)
    # still moving: open work orders, orders whose work order is not there yet, customer orders not yet following
    if ! echo "$line" | grep -Eq 'open=[1-9]|missing=[1-9]|lagging=[1-9]' || [ "$(date +%s)" -ge "$deadline" ]; then
        line=$(evaluate 1)
        break
    fi
    echo "    $line"
    sleep "$POLL_S"
done

echo "$line"
head -10 "$WORK/findings.txt" | sed 's/^/    /' >&2
accepted=$(echo "$line" | sed 's/.*accepted=\([0-9]*\).*/\1/')
done_=$(echo "$line" | sed 's/.*done=\([0-9]*\).*/\1/')
failed=$(echo "$line" | sed 's/.*failed=\([0-9]*\).*/\1/')
if echo "$line" | grep -q 'open=0 missing=0 double=0 unexpected=0 lagging=0 unlisted=0' \
    && [ $((done_ + failed)) -eq "$accepted" ] \
    && [ "$accepted" -gt 0 ]; then
    echo "RECONCILE OK: every custom order has exactly one finished work order"
else
    echo "RECONCILE FAILED"
    exit 1
fi
