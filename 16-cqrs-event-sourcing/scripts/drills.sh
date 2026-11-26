#!/usr/bin/env bash
# Drills of the event-sourced Forge, each on a fresh Compose stack (images from scripts/e2e.sh or BUILD=true).
#   drills.sh <1|2|3|4|5|all>
# 1 start race: 20 work orders, 5 smiths each at once -> exactly one 200 per work order, the rest 409
# 2 rebuild: checksum of read.workshop_board, rebuild from the journal -> the same checksum
# 3 late projection: smith-load off for 30 orders by 3 smiths, then on -> it catches up with the whole journal
# 4 kill -9 forge-service while orders flow and outcomes are pending -> nothing lost, >= 1 ForgeEvent per done order
# 5 300 orders -> projection lag p50/p95 (workshop_board.updated_at - last_event_recorded_at)
# Every drill ends with the reconciliation (scripts/reconcile.sh): each accepted custom order has exactly one
# WorkOrderRequested in forge.event. A drill whose failure did not happen fails as NOT STAGED.
# Results: target/drills/results.md.
set -euo pipefail

cd "$(dirname "$0")/.."   # 16-cqrs-event-sourcing
DRILL="${1:?drill 1..5|all}"
PROJECT=hephaestus-16
ORDERING=http://localhost:18080
FORGE=http://localhost:18081
RESULTS=target/drills/results.md
# Git Bash: MSYS_NO_PATHCONV keeps container paths (/opt/kafka/bin) from becoming C:/... (only here: curl
# on the host needs /dev/null translated)
compose() { MSYS_NO_PATHCONV=1 docker compose -f compose.yaml -p "$PROJECT" "$@"; }
# the status code, 000 when curl itself failed (never an exit under set -e)
code() { curl -s -o /dev/null -w '%{http_code}' "$@" || true; }
# post_ok <url>: POST that must answer 200
post_ok() {
    local status
    status=$(code -X POST "$1")
    [ "$status" = "200" ] || { echo "POST $1 -> $status" >&2; exit 1; }
}
sql() { compose exec -T postgres-forge psql -U forge -d forge -Atc "$1" | tr -d '\r'; }

mkdir -p target/drills
[ -f "$RESULTS" ] || printf '| drill | precondition | outcome | reconciliation | lag p50/p95 |\n|---|---|---|---|---|\n' > "$RESULTS"

if [ "${BUILD:-false}" = "true" ]; then
    ../mvnw -B -ntp -f pom.xml package jib:dockerBuild -DskipTests -Dspotless.check.skip=true -q
fi

wait_ready() {
    for url in "$ORDERING/actuator/health/readiness" "$FORGE/actuator/health/readiness"; do
        for _ in $(seq 1 90); do [ "$(code "$url")" = "200" ] && break; sleep 2; done
        [ "$(code "$url")" = "200" ] || { echo "$url not ready" >&2; return 1; }
    done
}

start_stack() {
    compose down -v > /dev/null 2>&1 || true
    compose up -d --wait > "$RUN/compose.log" 2>&1 || { cat "$RUN/compose.log"; exit 1; }
    wait_ready
}

# orders <seconds> | orders_n <count>: custom IRON-01 x1 every 200 ms; ids to $RUN/orders.txt
place_one() {
    local id
    id=$(curl -s -m 5 -X POST "$ORDERING/api/orders" -H 'Content-Type: application/json' \
        -d '{"customerId":"drill","custom":true,"material":"IRON-01","quantity":1}' 2> /dev/null \
        | sed -n 's/.*"id":"\([0-9a-f-]*\)".*/\1/p' || true)
    # one id per line: the response has no trailing newline
    if [ -n "$id" ]; then printf '%s\n' "$id" >> "$RUN/orders.txt"; fi
}
orders() { local end=$((SECONDS + $1)); while [ $SECONDS -lt $end ]; do place_one; sleep 0.2; done; }
orders_n() { for _ in $(seq 1 "$1"); do place_one; sleep 0.2; done; }

# work orders of the placed orders that are on the board with <status> (ids, one per line)
board() {
    curl -sf "$FORGE/api/work-orders?status=$1" | python3 -c 'import json,sys
placed={l.strip() for l in open(sys.argv[1]) if l.strip()}
for w in json.load(sys.stdin):
    if w["orderId"] in placed: print(w["id"])' "$RUN/orders.txt" | tr -d '\r'
}
# wait_board <status> <count> <seconds>
wait_board() {
    for _ in $(seq 1 "$3"); do
        [ "$(board "$1" | wc -l | tr -d ' ')" -ge "$2" ] && return 0
        sleep 1
    done
    echo "only $(board "$1" | wc -l) work orders $1 (expected $2)" >&2
    return 1
}
accepted() {
    local ids
    ids=$(sed "s/.*/'&'/" "$RUN/orders.txt" | paste -sd, -)
    compose exec -T postgres-ordering psql -U ordering -d ordering -Atc \
        "select count(*) from ordering.customer_order where quote_id is not null and id in ($ids)" | tr -d '\r'
}
# every projection at the journal head without an error
wait_caught_up() {
    for _ in $(seq 1 "${1:-60}"); do
        curl -sf "$FORGE/admin/projections" | python3 -c 'import json,sys
sys.exit(0 if all(p["lag"]==0 and p["lastError"]=="" for p in json.load(sys.stdin)) else 1)' && return 0
        sleep 1
    done
    echo "projections not caught up: $(curl -s "$FORGE/admin/projections")" >&2
    return 1
}
# A reconciliation over zero accepted orders proves nothing: it fails too
reconcile_ok() {
    scripts/reconcile.sh "$RUN/orders.txt" > "$RUN/reconcile.txt" || { cat "$RUN/reconcile.txt" >&2; return 1; }
    ! grep -q 'accepted=0 ' "$RUN/reconcile.txt" || { echo "no accepted order to reconcile" >&2; return 1; }
}
reconciled() { head -1 "$RUN/reconcile.txt" | tr -d '\r'; }
board_checksum() {
    sql "select md5(coalesce(string_agg(concat_ws('|', work_order_id, order_id, material, quantity, status, smith,
             failure_reason, last_event_recorded_at), ',' order by work_order_id), '')) from read.workshop_board"
}
# keys of es.forge.events, one per record
event_keys() {
    compose exec -T kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
        --topic es.forge.events --from-beginning --timeout-ms 30000 \
        --property print.key=true --property print.value=false 2> /dev/null | tr -d '\r' | cut -f1
}
lag() {
    local ids
    ids=$(sed "s/.*/'&'/" "$RUN/orders.txt" | paste -sd, -)
    sql "select extract(epoch from (updated_at - last_event_recorded_at)) * 1000 from read.workshop_board
         where order_id::text in ($ids)" > "$RUN/lag.txt"
    python3 - "$RUN/lag.txt" <<'PY'
import statistics, sys
ms = sorted(float(v) for v in open(sys.argv[1]).read().split())
q = statistics.quantiles(ms, n=100) if len(ms) > 1 else ms * 100
print(f"{q[49]:.0f} / {q[94]:.0f} ms (n={len(ms)})")
PY
}

# A failed drill keeps its evidence: the next drill starts with "compose down -v"
on_exit() {
    local status=$?
    if [ "$status" -ne 0 ] && [ -n "${RUN:-}" ] && [ -d "$RUN" ]; then
        compose ps -a > "$RUN/ps.txt" 2>&1 || true
        compose logs --no-color --tail=400 ordering forge-service kafka > "$RUN/logs.txt" 2>&1 || true
        curl -s -m 5 "$FORGE/admin/projections" > "$RUN/projections.json" 2>&1 || true
        echo "drill failed, logs in $RUN" >&2
    fi
}
trap on_exit EXIT
# under set -e a failing command ends the drill: say which one
trap 'echo "failed at line $LINENO: $BASH_COMMAND" >&2' ERR

not_staged() { echo "NOT STAGED: drill $1: $2" >&2; exit 1; }
row() { printf '| %s | %s | %s | %s | %s |\n' "$@" | tee -a "$RESULTS"; }

drill() {
    local n="$1"
    RUN="target/drills/$(date +%Y%m%d-%H%M%S)-$n"
    mkdir -p "$RUN"
    : > "$RUN/orders.txt"
    echo "==> drill $n"
    case "$n" in
        1)
            start_stack
            orders_n 20
            wait_board ALLOCATED 20 60
            board ALLOCATED > "$RUN/work-orders.txt"
            : > "$RUN/codes.txt"
            while read -r wo; do
                for smith in anna brokk sindri eitri volund; do
                    # "<work order> <code> race|refused|-": a 409 is either a lost race on the stream version
                    # (Concurrent change) or a start after another smith's start committed (Refused)
                    {
                        body=$(curl -s -w ' %{http_code}' -X POST "$FORGE/api/work-orders/$wo/start?smith=$smith")
                        kind=-
                        case "$body" in
                            *"Concurrent change"*) kind=race ;;
                            *"Refused by the work order"*) kind=refused ;;
                        esac
                        printf '%s %s %s\n' "$wo" "${body##* }" "$kind" >> "$RUN/codes.txt"
                    } &
                done
                wait
            done < "$RUN/work-orders.txt"
            races=$(grep -c ' 409 race$' "$RUN/codes.txt" || true)
            refused=$(grep -c ' 409 refused$' "$RUN/codes.txt" || true)
            # a refusal alone means the starts ran one after another: the optimistic lock was never tested
            [ "$races" -gt 0 ] || not_staged 1 "no start lost a race on the stream version ($refused refused one by one)"
            python3 - "$RUN/codes.txt" <<'PY' || exit 1
import collections, sys
codes = collections.defaultdict(list)
for line in open(sys.argv[1]):
    wo, code, _ = line.split()
    codes[wo].append(code)
bad = {wo: c for wo, c in codes.items() if c.count("200") != 1 or len(c) - c.count("200") != c.count("409")}
for wo, c in bad.items():
    print(f"FAIL {wo}: {c}", file=sys.stderr)
sys.exit(1 if bad or len(codes) != 20 else 0)
PY
            reconcile_ok
            row 1 "20 work orders, 5 smiths start each at once" \
                "one 200 per work order; 409: $races lost the race, $refused refused" \
                "$(reconciled)" -
            ;;
        2)
            start_stack
            orders_n 10
            wait_board ALLOCATED 10 60
            i=0
            for wo in $(board ALLOCATED); do
                i=$((i + 1))
                post_ok "$FORGE/api/work-orders/$wo/start?smith=anna"
                if [ $((i % 2)) -eq 0 ]; then post_ok "$FORGE/api/work-orders/$wo/complete"; fi
            done
            wait_caught_up
            rows=$(sql "select count(*) from read.workshop_board")
            [ "$rows" -gt 10 ] || not_staged 2 "the board has only $rows rows"
            before=$(board_checksum)
            [ "$(code -X POST "$FORGE/admin/projections/workshop-board/rebuild")" = "202" ] \
                || { echo "rebuild not accepted" >&2; exit 1; }
            wait_caught_up
            after=$(board_checksum)
            [ "$before" = "$after" ] || { echo "board after rebuild differs: $before -> $after" >&2; exit 1; }
            reconcile_ok
            row 2 "workshop-board with $rows rows, rebuilt from the journal" "checksum $before unchanged" \
                "$(reconciled)" -
            ;;
        3)
            export PROJECTIONS_SMITH_LOAD_ENABLED=false
            start_stack
            orders_n 30
            wait_board ALLOCATED 30 90
            i=0
            for wo in $(board ALLOCATED); do
                smiths=(anna brokk sindri)
                post_ok "$FORGE/api/work-orders/$wo/start?smith=${smiths[$((i % 3))]}"
                post_ok "$FORGE/api/work-orders/$wo/complete"
                i=$((i + 1))
            done
            sleep 5
            [ "$(sql "select count(*) from read.smith_load")" = "0" ] || not_staged 3 "smith-load was not off"
            unset PROJECTIONS_SMITH_LOAD_ENABLED
            compose up -d --wait forge-service > "$RUN/compose-enable.log" 2>&1
            wait_ready
            wait_caught_up 90
            journal=$(sql "select count(*) from forge.event where type = 'WorkOrderCompleted'")
            projected=$(sql "select coalesce(sum(done), 0) from read.smith_load")
            [ "$journal" = "$projected" ] || { echo "smith_load done $projected, journal $journal" >&2; exit 1; }
            for smith in anna brokk sindri; do
                [ "$(sql "select done from read.smith_load where smith = '$smith'")" = "10" ] \
                    || { echo "smith $smith: $(sql "select * from read.smith_load where smith = '$smith'")" >&2; exit 1; }
            done
            reconcile_ok
            row 3 "smith-load off for 30 orders by 3 smiths, then on" "caught up: $projected done = journal" \
                "$(reconciled)" -
            ;;
        4)
            # outcomes wait in the journal until the kill: the publisher polls only every 5 minutes here
            export PUBLISHER_POLL_INTERVAL=300s
            start_stack
            orders 20 &
            flow=$!
            sleep 6
            compose kill -s SIGKILL forge-service > /dev/null
            sleep 3
            compose start forge-service > /dev/null
            wait "$flow"
            wait_ready
            want=$(accepted)
            [ "$want" -gt 0 ] || not_staged 4 "no order accepted"
            wait_board ALLOCATED "$want" 120
            for wo in $(board ALLOCATED); do
                post_ok "$FORGE/api/work-orders/$wo/start?smith=anna"
                post_ok "$FORGE/api/work-orders/$wo/complete"
            done
            # outcomes still in the journal only: the publisher resumes from its checkpoint after the restart
            # (with its default interval again)
            pending=$(sql "select count(*) from forge.event e, read.projection_checkpoint c
                where c.name = 'forge-events-publisher' and e.type = 'WorkOrderCompleted'
                and (e.tx_id, e.global_position) > (c.tx_id, c.position)")
            compose kill -s SIGKILL forge-service > /dev/null
            [ "$pending" -gt 0 ] || not_staged 4 "the publisher had sent every outcome before the kill"
            unset PUBLISHER_POLL_INTERVAL
            compose up -d --wait forge-service > "$RUN/compose-restart.log" 2>&1
            wait_ready
            wait_caught_up 90
            sleep 10
            event_keys > "$RUN/event-keys.txt"
            done_ids=$(sql "select payload->>'orderId' from forge.event e where type = 'WorkOrderRequested'
                and exists (select 1 from forge.event c where c.stream_id = e.stream_id and c.type = 'WorkOrderCompleted')
                and payload->>'orderId' in ($(sed "s/.*/'&'/" "$RUN/orders.txt" | paste -sd, -))")
            unpublished=0
            for id in $done_ids; do grep -qFx "$id" "$RUN/event-keys.txt" || unpublished=$((unpublished + 1)); done
            [ "$unpublished" -eq 0 ] || { echo "$unpublished done orders without ForgeEvent" >&2; exit 1; }
            dups=$({ grep -Fxf "$RUN/orders.txt" "$RUN/event-keys.txt" || true; } | sort | uniq -d | wc -l | tr -d ' ')
            reconcile_ok
            row 4 "kill -9 forge-service twice (orders flowing; $pending outcomes pending)" \
                "$(echo "$done_ids" | wc -w | tr -d ' ') done, each published; $dups duplicates" "$(reconciled)" -
            ;;
        5)
            start_stack
            orders_n 300
            wait_caught_up 120
            reconcile_ok
            row 5 "300 orders at 5/s" "-" "$(reconciled)" "$(lag)"
            ;;
        *) echo "unknown drill $n" >&2 && exit 64 ;;
    esac
    compose down -v > /dev/null 2>&1 || true
}

if [ "$DRILL" = "all" ]; then
    for n in 1 2 3 4 5; do drill "$n"; done
else
    drill "$DRILL"
fi
echo "DRILLS OK — $RESULTS"
