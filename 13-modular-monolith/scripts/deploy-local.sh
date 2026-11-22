#!/usr/bin/env bash
# 13 on the local kind cluster with images built here (before CI has published them to GHCR):
# jib:dockerBuild -> kind load -> kubectl apply -k k8s/overlays/local in namespace hephaestus-13.
# Needs the platform with Kafka, CNPG and the Gateway: platform/bootstrap.sh scaling (or full).
#   13-modular-monolith/scripts/deploy-local.sh      (SKIP_BUILD=true reuses the images)
set -euo pipefail

cd "$(dirname "$0")/.."   # 13-modular-monolith
NAMESPACE=hephaestus-13
CLUSTER="${KIND_CLUSTER:-hephaestus}"

if [ "${SKIP_BUILD:-false}" != "true" ]; then
    echo "==> images"
    ../mvnw -B -ntp -f pom.xml package jib:dockerBuild -DskipTests -Dspotless.check.skip=true -q
fi
for image in modular forge-service supplier-stub; do
    kind load docker-image "hephaestus/modular-monolith-$image:jib" --name "$CLUSTER"
done

echo "==> manifests"
kubectl get namespace "$NAMESPACE" > /dev/null 2>&1 || kubectl create namespace "$NAMESPACE"
# Two applies: the topics belong to namespace kafka, the application to hephaestus-13 (the overlay sets it)
kubectl apply -f k8s/topics.yaml
kubectl apply -k k8s/overlays/local
# a re-deploy with the same tag: restart to pick up the newly loaded image
kubectl -n "$NAMESPACE" rollout restart deploy/modular deploy/forge-service deploy/supplier-stub > /dev/null
echo "==> waiting for the databases and the services"
kubectl -n "$NAMESPACE" wait --for=condition=Ready cluster/monolith-db cluster/forge-db --timeout=300s
kubectl -n "$NAMESPACE" rollout status deploy/modular deploy/forge-service deploy/supplier-stub --timeout=300s
echo "ok    13 deployed: http://workshop.localtest.me (or curl --resolve workshop.localtest.me:80:127.0.0.1)"
