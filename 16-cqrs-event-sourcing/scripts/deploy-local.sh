#!/usr/bin/env bash
# 16 on the local kind cluster with images built here (before CI has published them to GHCR):
# jib:dockerBuild -> kind load -> kubectl apply (topics, overlays/local) in hephaestus-16.
# Needs the platform with Kafka (Strimzi), CNPG and the Gateway: platform/bootstrap.sh scaling (or full).
#   16-cqrs-event-sourcing/scripts/deploy-local.sh      (SKIP_BUILD=true reuses the images)
set -euo pipefail

cd "$(dirname "$0")/.."   # 16-cqrs-event-sourcing
NAMESPACE=hephaestus-16
CLUSTER="${KIND_CLUSTER:-hephaestus}"

if [ "${SKIP_BUILD:-false}" != "true" ]; then
    echo "==> images"
    ../mvnw -B -ntp -f pom.xml package jib:dockerBuild -DskipTests -Dspotless.check.skip=true -q
fi
for image in ordering forge-service supplier-stub; do
    kind load docker-image "hephaestus/cqrs-es-$image:jib" --name "$CLUSTER"
done

echo "==> manifests"
kubectl get namespace "$NAMESPACE" > /dev/null 2>&1 || kubectl create namespace "$NAMESPACE"
# Two applies: the topics belong to namespace kafka, the application to hephaestus-16 (the overlay sets it)
kubectl apply -f k8s/topics.yaml
# The schema Job is replaced, not patched (its pod template is immutable)
kubectl -n "$NAMESPACE" delete job schema-init --ignore-not-found > /dev/null
kubectl apply -k k8s/overlays/local
kubectl -n "$NAMESPACE" rollout restart deploy/ordering deploy/forge-service deploy/supplier-stub > /dev/null

echo "==> waiting for the databases, the registry and the services"
kubectl -n "$NAMESPACE" wait --for=condition=Ready cluster/ordering-db cluster/forge-db --timeout=300s
kubectl -n "$NAMESPACE" wait --for=condition=complete job/schema-init --timeout=300s
kubectl -n "$NAMESPACE" rollout status deploy/ordering deploy/forge-service deploy/supplier-stub --timeout=300s
echo "ok    16 deployed: http://es.localtest.me (or curl -H 'Host: es.localtest.me' http://127.0.0.1)"
