#!/bin/sh
# Registers (or updates) the Debezium connector "ordering-outbox" in Kafka Connect and waits until it and its task
# run. Idempotent: PUT /connectors/<name>/config. POSIX sh (Git Bash, the curl image).
# Usage: register-connector.sh <connect-url> <config.json> [<url that must answer first>]
set -eu

CONNECT="${1:?connect url}"
CONFIG="${2:?connector config}"
READY="${3:-}"
NAME=ordering-outbox

wait_for() {
    for _ in $(seq 1 90); do
        curl -sf -o /dev/null "$1" && return 0
        sleep 2
    done
    echo "not reachable: $1" >&2
    exit 1
}

wait_for "$CONNECT/connectors"
# The outbox table and publication come from Ordering's Flyway migration: register after Ordering is up
[ -n "$READY" ] && wait_for "$READY"

status=$(curl -s -o /tmp/connector.out -w '%{http_code}' -X PUT "$CONNECT/connectors/$NAME/config" \
    -H 'Content-Type: application/json' --data @"$CONFIG")
case "$status" in
    200 | 201) ;;
    *) echo "register $NAME: HTTP $status" >&2 && cat /tmp/connector.out >&2 && exit 1 ;;
esac

for _ in $(seq 1 30); do
    state=$(curl -sf "$CONNECT/connectors/$NAME/status" || true)
    case "$state" in
        *'"tasks":[{"id":0,"state":"RUNNING"'*) echo "connector $NAME RUNNING" && exit 0 ;;
        *'"state":"FAILED"'*) echo "connector $NAME FAILED: $state" >&2 && exit 1 ;;
    esac
    sleep 2
done
echo "connector $NAME not RUNNING: $state" >&2
exit 1
