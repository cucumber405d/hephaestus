#!/usr/bin/env bash
# 16 on kind, through the Envoy Gateway: a quoted custom order travels Ordering -> es.forge.requests -> Forge's
# journal; the smith takes and completes it and the order becomes READY.
#   16-cqrs-event-sourcing/scripts/live.sh          (after scripts/deploy-local.sh or the Argo app)
# Env: GATEWAY (127.0.0.1), NAMESPACE (hephaestus-16).
set -euo pipefail

NAMESPACE="${NAMESPACE:-hephaestus-16}"
GATEWAY="${GATEWAY:-127.0.0.1}"
HOST=es.localtest.me
ok() { echo "ok    $*"; }
fail() { echo "FAIL  $*" >&2; exit 1; }
api() { curl -sf -H "Host: $HOST" "$@"; }
json() { python3 -c 'import json,sys
v=json.load(sys.stdin)
print("" if v.get(sys.argv[1]) is None else v[sys.argv[1]])' "$1"; }

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
[ -n "$wo" ] || fail "no work order for $id: the request did not reach Forge"
ok "work order $wo in Forge"

api -X POST "http://$GATEWAY/api/work-orders/$wo/start" > /dev/null || fail "start $wo"
api -X POST "http://$GATEWAY/api/work-orders/$wo/complete" > /dev/null || fail "complete $wo"
for _ in $(seq 1 60); do
    [ "$(api "http://$GATEWAY/api/orders/$id" | json status)" = "READY" ] && break
    sleep 1
done
[ "$(api "http://$GATEWAY/api/orders/$id" | json status)" = "READY" ] || fail "order $id not READY"
ok "order $id READY"
echo "LIVE OK"
