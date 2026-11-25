#!/usr/bin/env bash
# 15 on the local kind cluster with images built here (before CI has published them to GHCR):
# jib:dockerBuild + docker build connect/ -> kind load -> kubectl apply (topics, overlays/local) in hephaestus-15.
# Needs the platform with Kafka (Strimzi), CNPG and the Gateway: platform/bootstrap.sh scaling (or full).
#   15-cdc-debezium/scripts/deploy-local.sh      (SKIP_BUILD=true reuses the images)
set -euo pipefail

cd "$(dirname "$0")/.."   # 15-cdc-debezium
NAMESPACE=hephaestus-15
CLUSTER="${KIND_CLUSTER:-hephaestus}"

if [ "${SKIP_BUILD:-false}" != "true" ]; then
    echo "==> images"
    ../mvnw -B -ntp -f pom.xml package jib:dockerBuild -DskipTests -Dspotless.check.skip=true -q
    docker build -q -t hephaestus/cdc-debezium-connect:jib connect > /dev/null
fi
for image in ordering forge-service supplier-stub connect; do
    kind load docker-image "hephaestus/cdc-debezium-$image:jib" --name "$CLUSTER"
done

echo "==> manifests"
kubectl get namespace "$NAMESPACE" > /dev/null 2>&1 || kubectl create namespace "$NAMESPACE"
# Two applies: the topics belong to namespace kafka, the application to hephaestus-15 (the overlay sets it)
kubectl apply -f k8s/topics.yaml
# The schema Job is replaced, not patched (its pod template is immutable)
kubectl -n "$NAMESPACE" delete job schema-init --ignore-not-found > /dev/null
kubectl apply -k k8s/overlays/local
kubectl -n "$NAMESPACE" rollout restart deploy/ordering deploy/forge-service deploy/supplier-stub > /dev/null

echo "==> waiting for the databases, the registry, Kafka Connect and the services"
kubectl -n "$NAMESPACE" wait --for=condition=Ready cluster/ordering-db cluster/forge-db --timeout=300s
kubectl -n "$NAMESPACE" wait --for=condition=complete job/schema-init --timeout=300s
kubectl -n "$NAMESPACE" rollout status deploy/ordering deploy/forge-service deploy/supplier-stub --timeout=300s
kubectl -n "$NAMESPACE" wait --for=condition=Ready kafkaconnect/cdc-connect --timeout=300s
kubectl -n "$NAMESPACE" wait --for=condition=Ready kafkaconnector/ordering-outbox --timeout=300s
echo "ok    15 deployed: http://cdc.localtest.me (or curl -H 'Host: cdc.localtest.me' http://127.0.0.1)"
