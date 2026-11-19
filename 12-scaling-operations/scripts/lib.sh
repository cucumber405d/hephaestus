#!/usr/bin/env bash
# Helpers shared by peak.sh and its self-tests (sourced).

# Which worker may be drained without taking platform state down (ADR-0005): reads
# `kubectl get pods -A -o wide --no-headers` on stdin, prints a worker that hosts no Kafka broker, no lgtm and no
# CNPG database pod (*-db-<n>); exit 1 if every worker hosts one. WORKERS: the kind workers to choose from.
pick_drain_node() {
    local workers="${WORKERS:-hephaestus-worker hephaestus-worker2}"
    local stateful
    stateful=$(awk '$1 == "kafka" || $1 == "observability" || $2 ~ /-db-[0-9]+$/ { print $8 }' | sort -u)
    local node
    for node in $workers; do
        if ! echo "$stateful" | grep -qx "$node"; then
            echo "$node"
            return 0
        fi
    done
    return 1
}
