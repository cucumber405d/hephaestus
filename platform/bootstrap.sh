#!/usr/bin/env bash
# Bootstraps the shared platform:
#   1. kind cluster "hephaestus" (unless it exists; CI creates it with helm/kind-action),
#   2. Argo CD via Helm (the only imperative install),
#   3. the root Application "platform" (App-of-Apps) for a profile and a Git revision.
# From then on Argo CD pulls everything else from Git, including its own configuration.
#   platform/bootstrap.sh [minimal|full] [git-revision]      (defaults: minimal, main)
# The revision must exist on GitHub: Argo CD reads the repository, not the local working tree.
# Needs Docker, kind, kubectl, helm.
set -euo pipefail

PROFILE="${1:-minimal}"
REVISION="${2:-main}"
CLUSTER=hephaestus
REPO_URL=https://github.com/cucumber405d/hephaestus.git

cd "$(dirname "$0")"   # platform
if [ ! -f "apps/values-$PROFILE.yaml" ]; then
    echo "Unknown profile: $PROFILE (expected minimal or full)" >&2
    exit 2
fi
# Single source of truth for the Argo CD chart version: the App-of-Apps values (Argo CD manages itself with it)
ARGOCD_CHART_VERSION=$(sed -n 's/^argocdChartVersion: *//p' apps/values.yaml)

if ! kind get clusters 2>/dev/null | grep -qx "$CLUSTER"; then
    kind create cluster --config kind/cluster.yaml
fi
kubectl config use-context "kind-$CLUSTER"

echo "==> Argo CD $ARGOCD_CHART_VERSION"
helm repo add argo https://argoproj.github.io/argo-helm --force-update >/dev/null
helm upgrade --install argocd argo/argo-cd --version "$ARGOCD_CHART_VERSION" \
    --namespace argocd --create-namespace -f argocd/values.yaml --wait --timeout 10m

echo "==> root Application: profile $PROFILE, revision $REVISION"
kubectl apply -f - <<YAML
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: platform
  namespace: argocd
spec:
  project: default
  source:
    repoURL: $REPO_URL
    targetRevision: $REVISION
    path: platform/apps
    helm:
      valueFiles:
        - values-$PROFILE.yaml
      parameters:
        - name: targetRevision
          value: $REVISION
  destination:
    server: https://kubernetes.default.svc
    namespace: argocd
  syncPolicy:
    automated:
      prune: true
      selfHeal: true
YAML

password=$(kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' | base64 -d)
cat <<INFO

Argo CD is syncing the platform. Watch:  kubectl -n argocd get applications -w
UI (once the gateway is up): http://argocd.localtest.me   user admin, password $password
Then:                         05-platform-gitops/scripts/smoke.sh $PROFILE
INFO
