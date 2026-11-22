#!/usr/bin/env bash
# Self-test of reconcile.sh without databases: the three dumps come from fixture files.
#   bash 13-modular-monolith/scripts/test/reconcile-fixture.sh
set -uo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
RECONCILE="$HERE/../reconcile.sh"
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
failures=0

run() { # name expected-exit [grep pattern]
    local out code
    out=$(ORDERS_DUMP_CMD="${ORDERS_CMD:-cat $WORK/orders.txt}" MONOLITH_WO_DUMP_CMD="cat $WORK/monolith.txt" \
        SERVICE_WO_DUMP_CMD="${SERVICE_CMD:-cat $WORK/service.txt}" WAIT_S=2 POLL_S=1 \
        bash "$RECONCILE" "$WORK/orders.csv" 2>&1)
    code=$?
    if [ "$code" -ne "$2" ] || { [ -n "${3:-}" ] && ! echo "$out" | grep -q -- "$3"; }; then
        echo "FAIL  $1 (exit $code, want $2${3:+, want output '$3'})"
        echo "$out" | sed 's/^/      /'
        failures=$((failures + 1))
    else
        echo "ok    $1"
    fi
}

good() {
    printf 'o1,IRON-01,DONE\no2,STEEL-01,DONE\no3,MITHRIL-01,FAILED\n' > "$WORK/orders.csv"
    printf 'o1|READY|\no2|READY|\no3|REJECTED|material not deliverable: MITHRIL-01\n' > "$WORK/orders.txt"
    # o1 finished in the monolith before the cutover, o2 and o3 in the service after it
    printf 'o1|WO-000001|DONE\n' > "$WORK/monolith.txt"
    printf 'o2|FW-a|DONE\no3|FW-b|FAILED\n' > "$WORK/service.txt"
}

good
run "both sides together, as expected" 0 "accepted=3 done=2 failed=1 open=0 missing=0 double=0 unexpected=0"

good; printf 'o2|WO-000002|DONE\n' >> "$WORK/monolith.txt"
run "a work order on both sides is double" 1 "double=1"

good; printf 'o1|WO-000001|DONE\n' > "$WORK/monolith.txt"; printf 'o3|FW-b|FAILED\n' > "$WORK/service.txt"
run "an order without any work order is missing" 1 "missing=1"

good; printf 'o2|FW-a|IN_PROGRESS\no3|FW-b|FAILED\n' > "$WORK/service.txt"
run "a work order still open at the deadline fails" 1 "open=1"

good; printf 'o1|READY|\no2|REJECTED|late\no3|REJECTED|material not deliverable: MITHRIL-01\n' > "$WORK/orders.txt"
run "a customer order disagreeing with its work order is unexpected" 1 "unexpected=1"

good; SERVICE_CMD="false" run "an unreadable service database fails (never OK)" 1 "RECONCILE FAILED"

good; : > "$WORK/service.txt"; : > "$WORK/monolith.txt"
run "empty work order tables fail" 1 "missing=3"

good; printf 'o1,IRON-01,DONE\r\no2,STEEL-01,DONE\r\no3,MITHRIL-01,FAILED\r\n' > "$WORK/orders.csv"
ORDERS_CMD="sed 's/\$/\r/' $WORK/orders.txt" run "CRLF in CSV and dumps compares like LF" 0 "unexpected=0"

# The totals come from the CSV, not from the dumps: an empty customer-order table cannot make orders vanish
good; : > "$WORK/orders.txt"
run "an empty customer-order table fails" 1 "RECONCILE FAILED"

good; : > "$WORK/orders.csv"
run "no accepted order at all is not a success" 1 "RECONCILE FAILED"

[ "$failures" -eq 0 ] && echo "reconcile self-test OK" || { echo "reconcile self-test: $failures failure(s)"; exit 1; }
