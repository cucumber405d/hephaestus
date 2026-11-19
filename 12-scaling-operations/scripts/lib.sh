#!/usr/bin/env bash
# Helpers shared by peak.sh and its self-tests (sourced).

# The pod listing pick_drain_node reads: namespace, name, node (-o wide would put "1 (3h ago)" restarts in between)
pods_by_node() {
    kubectl get pods -A --no-headers -o custom-columns=NS:.metadata.namespace,NAME:.metadata.name,NODE:.spec.nodeName
}

# Which worker may be drained without taking platform state down (ADR-0005): reads pods_by_node on stdin, prints a
# worker that hosts no Kafka broker, no lgtm, no Envoy proxy of the Gateway (the HTTP entry) and no CNPG database pod
# (*-db-<n>); exit 1 if every worker hosts one. WORKERS: the kind workers to choose from.
pick_drain_node() {
    local workers="${WORKERS:-hephaestus-worker hephaestus-worker2}"
    local stateful
    # Only the pods a drain would take down with state or traffic: Kafka brokers (StrimziPodSet pods end in an ordinal,
    # the operators in a ReplicaSet hash), lgtm, the Gateway's Envoy proxies (the controller envoy-gateway-<hash> is
    # an ordinary Deployment) and CNPG instances. Other platform pods are rescheduled like any Deployment.
    stateful=$(awk '($1 == "kafka" && $2 ~ /-[0-9]+$/) \
            || ($1 == "observability" && $2 ~ /^lgtm/) \
            || ($1 == "envoy-gateway-system" && $2 !~ /^envoy-gateway-/) \
            || $2 ~ /-db-[0-9]+$/ { print $3 }' \
        | sort -u)
    local node
    for node in $workers; do
        if ! echo "$stateful" | grep -qx "$node"; then
            echo "$node"
            return 0
        fi
    done
    return 1
}
