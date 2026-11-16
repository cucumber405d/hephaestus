#!/usr/bin/env bash
# Creates the kind cluster "hephaestus-04" (if missing) and deploys the Catalog into it.
# Needs Docker, kind and kubectl.
set -euo pipefail

cd "$(dirname "$0")/.."   # 04-k8s-basics
CLUSTER=hephaestus-04

if ! kind get clusters 2>/dev/null | grep -qx "$CLUSTER"; then
    kind create cluster --config kind/cluster.yaml
fi
kubectl config use-context "kind-$CLUSTER"
scripts/deploy.sh
