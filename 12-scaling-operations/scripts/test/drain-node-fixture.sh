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

# kubectl get pods -A --no-headers -o custom-columns=NS:.metadata.namespace,NAME:.metadata.name,NODE:.spec.nodeName
# (not -o wide: its RESTARTS column "1 (3h ago)" is several words and shifts the node column)
check "the worker without stateful pods" 0 hephaestus-worker2 "\
kafka            platform-dual-role-0           hephaestus-worker
observability    lgtm-5d8f-abc                  hephaestus-worker
hephaestus-12    payment-db-1                   hephaestus-worker
hephaestus-12    payment-7c9-xyz                hephaestus-worker2
argocd           argocd-server-1                hephaestus-worker2"

check "a CNPG database counts as stateful" 0 hephaestus-worker "\
hephaestus-12    ordering-7c9-abc               hephaestus-worker
hephaestus-12    ordering-db-1                  hephaestus-worker2"

check "both workers stateful: refuse" 1 "" "\
kafka            platform-dual-role-0           hephaestus-worker
observability    lgtm-5d8f-abc                  hephaestus-worker2"

check "the control plane is never drained" 0 hephaestus-worker2 "\
kafka            platform-dual-role-0           hephaestus-worker
kube-system      etcd-hephaestus-control-plane  hephaestus-control-plane"

check "the Gateway's Envoy counts as stateful (the HTTP entry)" 0 hephaestus-worker "\
envoy-gateway-system envoy-platform-abc     hephaestus-worker2
hephaestus-12    payment-7c9-xyz                hephaestus-worker"

# Stateless platform pods (operators, the Envoy Gateway controller) are rescheduled by a drain like any Deployment:
# they must not pin a worker. The names are the real ones of the platform (Strimzi, Envoy Gateway).
check "Kafka operators and the Envoy Gateway controller do not pin a worker" 0 hephaestus-worker2 "\
kafka                platform-dual-role-0                                          hephaestus-worker
kafka                platform-entity-operator-b676575d9-66c5q                      hephaestus-worker2
kafka                strimzi-cluster-operator-6d5f7c8b9-x2k4p                      hephaestus-worker2
envoy-gateway-system envoy-envoy-gateway-system-platform-f2076d6c-bb7c575bf-fqwc6  hephaestus-worker
envoy-gateway-system envoy-gateway-6c95454bdb-vlvc9                                hephaestus-worker2
observability        lgtm-5d8f9c7b6-abcde                                          hephaestus-worker"

[ "$failures" -eq 0 ] && echo "drain-node self-test OK" || { echo "drain-node self-test: $failures failure(s)"; exit 1; }
