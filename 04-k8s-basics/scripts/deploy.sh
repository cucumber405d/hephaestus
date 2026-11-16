#!/usr/bin/env bash
# Deploys the local overlay in a fixed order into the current kubectl context:
#   namespace, config, PostgreSQL -> migration Job (until complete) -> API Deployment (until rolled out).
# Kubernetes itself has no ordering between objects; without this the API would crash-loop until the schema exists.
set -euo pipefail

cd "$(dirname "$0")/.."   # 04-k8s-basics
NS=hephaestus-04
OVERLAY=k8s/overlays/local

apply() { kubectl apply -k "$OVERLAY" -l "$1"; }

echo "==> namespace, config, database"
apply 'app.kubernetes.io/component in (namespace,config,database)'
kubectl -n "$NS" rollout status statefulset/postgres --timeout=180s

echo "==> migration"
# A Job's pod template is immutable and a finished Job never runs again: replace it on every deploy.
# Flyway makes the re-run a no-op when there is nothing new to migrate.
kubectl -n "$NS" delete job catalog-migrate --ignore-not-found --wait=true
apply app.kubernetes.io/component=migration
for _ in $(seq 1 150); do
    succeeded=$(kubectl -n "$NS" get job catalog-migrate -o jsonpath='{.status.succeeded}')
    failed=$(kubectl -n "$NS" get job catalog-migrate -o jsonpath='{.status.conditions[?(@.type=="Failed")].status}')
    if [ "$succeeded" = "1" ]; then
        break
    fi
    if [ "$failed" = "True" ]; then
        echo "Migration job failed:" >&2
        kubectl -n "$NS" logs job/catalog-migrate --tail=100 >&2 || true
        exit 1
    fi
    sleep 2
done
if [ "$succeeded" != "1" ]; then
    echo "Migration job did not complete in time" >&2
    kubectl -n "$NS" describe job catalog-migrate >&2 || true
    exit 1
fi

echo "==> api"
apply app.kubernetes.io/component=api
kubectl -n "$NS" rollout status deployment/catalog --timeout=300s
