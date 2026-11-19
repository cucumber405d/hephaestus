#!/usr/bin/env bash
# Self-test of pick_drain_node (lib.sh): the drained worker must host no Kafka, no lgtm and no CNPG database pod.
#   bash 12-scaling-operations/scripts/test/drain-node-fixture.sh
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=../lib.sh
. "$HERE/../lib.sh"
failures=0
check() { # name want-exit want-node pods-fixture
    local node code
    node=$(echo "$4" | pick_drain_node)
    code=$?
    if [ "$code" -ne "$2" ] || [ "$node" != "$3" ]; then
        echo "FAIL  $1: got '$node' (exit $code), want '$3' (exit $2)"
        failures=$((failures + 1))
    else
        echo "ok    $1"
    fi
}

# kubectl get pods -A -o wide --no-headers: NAMESPACE NAME READY STATUS RESTARTS AGE IP NODE NOMINATED READINESS
check "the worker without stateful pods" 0 hephaestus-worker2 "\
kafka            platform-dual-role-0      1/1 Running 0 1h 10.0.0.1 hephaestus-worker <none> <none>
observability    lgtm-5d8f-abc             1/1 Running 0 1h 10.0.0.2 hephaestus-worker <none> <none>
hephaestus-12    payment-db-1              1/1 Running 0 1h 10.0.0.3 hephaestus-worker <none> <none>
hephaestus-12    payment-7c9-xyz           1/1 Running 0 1h 10.0.0.4 hephaestus-worker2 <none> <none>
argocd           argocd-server-1           1/1 Running 0 1h 10.0.0.5 hephaestus-worker2 <none> <none>"

check "a CNPG database counts as stateful" 0 hephaestus-worker "\
hephaestus-12    ordering-7c9-abc          1/1 Running 0 1h 10.0.0.1 hephaestus-worker <none> <none>
hephaestus-12    ordering-db-1             1/1 Running 0 1h 10.0.0.3 hephaestus-worker2 <none> <none>"

check "both workers stateful: refuse" 1 "" "\
kafka            platform-dual-role-0      1/1 Running 0 1h 10.0.0.1 hephaestus-worker <none> <none>
observability    lgtm-5d8f-abc             1/1 Running 0 1h 10.0.0.2 hephaestus-worker2 <none> <none>"

check "the control plane is never drained" 0 hephaestus-worker2 "\
kafka            platform-dual-role-0      1/1 Running 0 1h 10.0.0.1 hephaestus-worker <none> <none>
kube-system      etcd-hephaestus-control-plane 1/1 Running 0 1h 10.0.0.9 hephaestus-control-plane <none> <none>"

[ "$failures" -eq 0 ] && echo "drain-node self-test OK" || { echo "drain-node self-test: $failures failure(s)"; exit 1; }
