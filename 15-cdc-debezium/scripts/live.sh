#!/usr/bin/env bash
# 15 on kind, through the Envoy Gateway: a quoted custom order travels Ordering -> outbox -> Debezium (Kafka Connect)
# -> cdc.forge.requests -> Forge; the smith completes it and the order becomes READY.
#   15-cdc-debezium/scripts/live.sh          (after scripts/deploy-local.sh or the Argo app)
# Env: GATEWAY (127.0.0.1), NAMESPACE (hephaestus-15).
set -euo pipefail

NAMESPACE="${NAMESPACE:-hephaestus-15}"
GATEWAY="${GATEWAY:-127.0.0.1}"
HOST=cdc.localtest.me
ok() { echo "ok    $*"; }
fail() { echo "FAIL  $*" >&2; exit 1; }
api() { curl -sf -H "Host: $HOST" "$@"; }
json() { python3 -c 'import json,sys
v=json.load(sys.stdin)
print("" if v.get(sys.argv[1]) is None else v[sys.argv[1]])' "$1"; }

ready=$(kubectl -n "$NAMESPACE" get kafkaconnector ordering-outbox \
    -o jsonpath='{.status.conditions[?(@.type=="Ready")].status}')
[ "$ready" = "True" ] || fail "KafkaConnector ordering-outbox not Ready"
ok "KafkaConnector ordering-outbox Ready"
# Ready can hide a FAILED task; the task itself must run
task=$(kubectl -n "$NAMESPACE" get kafkaconnector ordering-outbox \
    -o jsonpath='{.status.connectorStatus.tasks[0].state}')
[ "$task" = "RUNNING" ] || fail "connector task is '$task', not RUNNING"
ok "connector task RUNNING"

id=$(api -X POST "http://$GATEWAY/api/orders" -H 'Content-Type: application/json' \
    -d '{"customerId":"live","custom":true,"material":"IRON-01","quantity":5}' | json id)
[ "$(api "http://$GATEWAY/api/orders/$id" | json status)" = "ACCEPTED" ] || fail "order $id not ACCEPTED"
ok "order $id accepted with a quote"

wo=""
for _ in $(seq 1 60); do
    wo=$(api "http://$GATEWAY/api/work-orders" | python3 -c 'import json,sys
print(next((w["id"] for w in json.load(sys.stdin) if w["orderId"]==sys.argv[1]),""))' "$id")
    [ -n "$wo" ] && break
    sleep 1
done
[ -n "$wo" ] || fail "no work order for $id: the outbox row did not reach Forge"
ok "work order $wo in Forge (delivered by Debezium)"

api -X POST "http://$GATEWAY/api/work-orders/$wo/start" > /dev/null || fail "start $wo"
api -X POST "http://$GATEWAY/api/work-orders/$wo/complete" > /dev/null || fail "complete $wo"
for _ in $(seq 1 60); do
    [ "$(api "http://$GATEWAY/api/orders/$id" | json status)" = "READY" ] && break
    sleep 1
done
[ "$(api "http://$GATEWAY/api/orders/$id" | json status)" = "READY" ] || fail "order $id not READY"
ok "order $id READY"
echo "LIVE OK"
