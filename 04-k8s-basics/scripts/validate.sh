#!/usr/bin/env bash
# Renders the local overlay and validates it against the Kubernetes schemas; needs no cluster.
# Needs kubectl (kustomize is built in) and kubeconform (https://github.com/yannh/kubeconform).
set -euo pipefail

cd "$(dirname "$0")/.."   # 04-k8s-basics
kubectl kustomize k8s/overlays/local > /tmp/04-k8s-basics-rendered.yaml
kubeconform -strict -summary /tmp/04-k8s-basics-rendered.yaml
