#!/usr/bin/env bash
# Failure drills: the same failure against both outbox modes, and how each recovered.
#   drills.sh <1|2|3|4|5|all> <debezium|modulith>
# 1 Kafka unavailable 30 s     2 Ordering killed (kill -9) with requests not yet sent
# 3 Kafka Connect stopped 60 s (debezium only: retained WAL grows)
# 4 Kafka Connect killed before its offset flush (debezium only: duplicates, still one work order each)
# 5 latency order -> work order, p50/p95
# A drill whose failure did not happen fails as NOT STAGED — it never passes without the failure.
# Each drill runs on a fresh stack (images from scripts/e2e.sh or BUILD=true). Results: target/drills/results.md.
set -euo pipefail

cd "$(dirname "$0")/.."   # 15-cdc-debezium
DRILL="${1:?drill 1..5|all}"
MODE="${2:?debezium|modulith}"
PROJECT=hephaestus-15
ORDERING=http://localhost:18080
RESULTS=target/drills/results.md
compose() { docker compose -f compose.yaml -p "$PROJECT" "$@"; }
code() { curl -s -o /dev/null -w '%{http_code}' "$@"; }
say() { echo "      $*"; }

mkdir -p target/drills
[ -f "$RESULTS" ] || printf '| drill | mode | precondition | recovered by | missing before → after | duplicates | latency p50/p95 |\n|---|---|---|---|---|---|---|\n' > "$RESULTS"

if [ "${BUILD:-false}" = "true" ]; then
    ../mvnw -B -ntp -f pom.xml package jib:dockerBuild -DskipTests -Dspotless.check.skip=true -q
fi

wait_ready() {
    for _ in $(seq 1 90); do
        [ "$(code "$ORDERING/actuator/health/readiness")" = "200" ] && return 0
        sleep 2
    done
    echo "ordering not ready" >&2
    return 1
}

start_stack() {
    compose down -v > /dev/null 2>&1 || true
    OUTBOX_MODE="$MODE" compose up -d --wait > "$RUN/compose.log" 2>&1 || { cat "$RUN/compose.log"; exit 1; }
    wait_ready
    OUTBOX_MODE="$MODE" compose --profile init run --rm --no-deps connector-init > "$RUN/connector.log" 2>&1 \
        || { cat "$RUN/connector.log"; exit 1; }
}

# orders <seconds> | orders_n <count>: custom IRON-01 x1 every 200 ms; accepted ids to $RUN/orders.txt
place_one() {
    curl -s -m 5 -X POST "$ORDERING/api/orders" -H 'Content-Type: application/json' \
        -d '{"customerId":"drill","custom":true,"material":"IRON-01","quantity":1}' 2> /dev/null \
        | sed -n 's/.*"id":"\([0-9a-f-]*\)".*/\1/p' >> "$RUN/orders.txt" || true
}
orders() { local end=$((SECONDS + $1)); while [ $SECONDS -lt $end ]; do place_one; sleep 0.2; done; }
orders_n() { for _ in $(seq 1 "$1"); do place_one; sleep 0.2; done; }

# reconcile.sh exits 1 when something is missing: here that is an answer, not an error (set -e, pipefail)
# Modulith publications not yet completed: the relay's requests that are stuck in Ordering
incomplete() {
    compose exec -T postgres-ordering psql -U ordering -d ordering -Atc \
        "select count(*) from event_publication where completion_date is null"
}
missing() { { scripts/reconcile.sh "$RUN/orders.txt" || true; } | sed -n 's/.*missing=\([0-9]*\).*/\1/p'; }
reconcile_ok() { scripts/reconcile.sh "$RUN/orders.txt" > "$RUN/reconcile.txt"; }
retained_wal() {
    compose exec -T postgres-ordering psql -U ordering -d ordering -Atc \
        "select coalesce(pg_wal_lsn_diff(pg_current_wal_lsn(), confirmed_flush_lsn), 0)::bigint
         from pg_replication_slots where slot_name = 'cdc_outbox'"
}
# keys of the placed orders that have more than one record on the topic
duplicates() {
    compose exec -T kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
        --topic cdc.forge.requests --from-beginning --timeout-ms 10000 \
        --property print.key=true --property print.value=false 2> /dev/null \
        | { grep -Fxf "$RUN/orders.txt" || true; } | sort | uniq -d | wc -l | tr -d ' '
}
latency() {
    local ids
    ids=$(sed "s/.*/'&'/" "$RUN/orders.txt" | paste -sd, -)
    compose exec -T postgres-ordering psql -U ordering -d ordering -Atc \
        "select id, extract(epoch from created_at) from ordering.customer_order where id in ($ids)" > "$RUN/placed.txt"
    compose exec -T postgres-forge psql -U forge -d forge -Atc \
        "select order_id, extract(epoch from created_at) from work_order where order_id in ($ids)" > "$RUN/done.txt"
    python3 - "$RUN/placed.txt" "$RUN/done.txt" <<'PY'
import statistics, sys
read = lambda p: dict(line.split("|") for line in open(p).read().split() if "|" in line)
placed, done = read(sys.argv[1]), read(sys.argv[2])
ms = sorted((float(done[k]) - float(placed[k])) * 1000 for k in done if k in placed)
q = statistics.quantiles(ms, n=100) if len(ms) > 1 else ms * 100
print(f"{q[49]:.0f} / {q[94]:.0f} ms (n={len(ms)})")
PY
}

not_staged() { echo "NOT STAGED: drill $1 ($MODE): $2" >&2; exit 1; }
row() { printf '| %s | %s | %s | %s | %s | %s | %s |\n' "$@" | tee -a "$RESULTS"; }

drill() {
    local n="$1"
    RUN="target/drills/$(date +%Y%m%d-%H%M%S)-$n-$MODE"
    mkdir -p "$RUN"
    : > "$RUN/orders.txt"
    echo "==> drill $n ($MODE)"
    case "$n" in
        1)
            if [ "$MODE" = "modulith" ]; then
                # With Kafka's defaults the producer waits up to 60 s for metadata and keeps a record for 120 s:
                # a 30 s outage would only delay the relay. Short timeouts make the sends fail, as in a long outage.
                export KAFKA_PRODUCER_MAX_BLOCK_MS=5000 KAFKA_PRODUCER_REQUEST_TIMEOUT_MS=10000 \
                    KAFKA_PRODUCER_DELIVERY_TIMEOUT_MS=15000
            fi
            start_stack
            unset KAFKA_PRODUCER_MAX_BLOCK_MS KAFKA_PRODUCER_REQUEST_TIMEOUT_MS KAFKA_PRODUCER_DELIVERY_TIMEOUT_MS
            compose pause kafka > /dev/null
            orders 30
            compose unpause kafka > /dev/null
            [ -s "$RUN/orders.txt" ] || not_staged 1 "no order accepted while Kafka was paused"
            if [ "$MODE" = "modulith" ]; then
                sleep 30
                stuck=$(incomplete)
                before=$(missing)
                [ "$stuck" -gt 0 ] || not_staged 1 "no publication left incomplete: every send succeeded"
                compose restart ordering > /dev/null
                wait_ready
                sleep 60
                [ "$(incomplete)" -eq 0 ] || { echo "publications still incomplete: $(incomplete)" >&2; exit 1; }
                reconcile_ok || { cat "$RUN/reconcile.txt"; exit 1; }
                row 1 "$MODE" "Kafka paused 30 s, $stuck publications incomplete" "restart of ordering" "$before → 0" - -
            else
                sleep 60
                before=$(missing)
                reconcile_ok || { cat "$RUN/reconcile.txt"; exit 1; }
                row 1 "$MODE" "Kafka paused 30 s" "itself" "$before → 0" - -
            fi
            ;;
        2)
            start_stack
            compose pause kafka > /dev/null
            orders 10
            compose kill -s SIGKILL ordering > /dev/null
            compose unpause kafka > /dev/null
            [ -s "$RUN/orders.txt" ] || not_staged 2 "no order accepted before the kill"
            sleep 30
            before=$(missing)
            if [ "$MODE" = "modulith" ]; then
                [ "$before" -gt 0 ] || not_staged 2 "nothing stuck before the kill"
                compose start ordering > /dev/null
                wait_ready
                sleep 30
                reconcile_ok || { cat "$RUN/reconcile.txt"; exit 1; }
                row 2 "$MODE" "kill -9 ordering, $before unsent" "restart of ordering" "$before → 0" - -
            else
                # delivered from the WAL while Ordering is still dead
                [ -z "$(compose ps -q --status running ordering)" ] || not_staged 2 "ordering is running again"
                reconcile_ok || { cat "$RUN/reconcile.txt"; exit 1; }
                row 2 "$MODE" "kill -9 ordering" "itself, ordering still down" "$before → 0" - -
                compose start ordering > /dev/null
            fi
            ;;
        3)
            [ "$MODE" = "debezium" ] || { echo "drill 3 is debezium only"; return 0; }
            start_stack
            orders 5
            sleep 10
            wal_before=$(retained_wal)
            compose stop connect > /dev/null
            orders 60
            wal_down=$(retained_wal)
            [ "$wal_down" -gt "$wal_before" ] || not_staged 3 "retained WAL did not grow ($wal_before -> $wal_down)"
            before=$(missing)
            [ "$before" -gt 0 ] || not_staged 3 "nothing missing while Connect was down"
            compose start connect > /dev/null
            # several offset flushes (10 s) and heartbeats (10 s) after the catch-up
            sleep 90
            reconcile_ok || { cat "$RUN/reconcile.txt"; exit 1; }
            wal_after=$(retained_wal)
            [ "$wal_after" -lt "$wal_down" ] || { echo "retained WAL not released: $wal_down -> $wal_after bytes" >&2; exit 1; }
            row 3 "$MODE" "Connect stopped 60 s, WAL $wal_before → $wal_down B" "itself, WAL back to $wal_after B" "$before → 0" - -
            ;;
        4)
            [ "$MODE" = "debezium" ] || { echo "drill 4 is debezium only"; return 0; }
            export OFFSET_FLUSH_INTERVAL_MS=60000
            start_stack
            orders 20
            sleep 5
            compose kill -s SIGKILL connect > /dev/null
            compose start connect > /dev/null
            sleep 60
            dup=$(duplicates)
            [ "$dup" -gt 0 ] || not_staged 4 "Connect sent nothing twice"
            reconcile_ok || { cat "$RUN/reconcile.txt"; exit 1; }
            unset OFFSET_FLUSH_INTERVAL_MS
            row 4 "$MODE" "kill -9 connect before the offset flush" "itself" "0 → 0" "$dup orders sent twice, one work order each" -
            ;;
        5)
            start_stack
            orders_n 300
            sleep 30
            reconcile_ok || { cat "$RUN/reconcile.txt"; exit 1; }
            row 5 "$MODE" "300 orders at 5/s" - "0 → 0" - "$(latency)"
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
echo "DRILLS OK ($MODE) — $RESULTS"
