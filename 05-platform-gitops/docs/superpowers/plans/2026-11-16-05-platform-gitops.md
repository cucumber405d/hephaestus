# 05-platform-gitops Implementation Plan

**Goal:** Общая платформа под Argo CD (App-of-Apps) в kind: Envoy Gateway, CNPG, Strimzi, Keycloak, Redis, otel-lgtm, Catalog-демо; smoke и CI.

**Spec:** `05-platform-gitops/docs/superpowers/specs/2026-11-16-05-platform-gitops-design.md`

**Execution:** inline, «всё сразу» (решение Dmitry); прогон — Dmitry локально и CI на PR; одно независимое ревью ветки в конце.

## Global Constraints
- Версии: argo-cd chart 10.7.0; gateway-helm v1.8.3; cloudnative-pg 0.29.0; strimzi-kafka-operator 1.2.0; keycloak-k8s-resources 26.8.1; grafana/otel-lgtm 0.30.0; redis 8; postgres образ — по умолчанию CNPG.
- Репозиторий `https://github.com/cucumber405d/hephaestus.git`; ревизия — values `targetRevision` (по умолчанию `main`).
- Хосты: `argocd.localtest.me`, `grafana.localtest.me`, `catalog.localtest.me`, `keycloak.localtest.me`; Gateway NodePort 30080 ↔ host 80.
- Комментарии в YAML/bash — английский; docs/impact — RU (+EN в impact). Impact-файл в каждом коммите.

## Tasks
1. Кластер и bootstrap: `platform/kind/cluster.yaml`, `argocd/values.yaml`, `bootstrap.sh`, `down.sh`.
2. App-of-Apps: Helm-чарт `platform/apps` (шаблоны на компоненты, профили).
3. Компоненты: gateway, observability, catalog, kafka, keycloak, redis.
4. Smoke и CI: `05-platform-gitops/scripts/smoke.sh`, `.github/workflows/05-platform-gitops.yml`, бейдж.
5. Документация: `platform/README.md`, `05-platform-gitops/{README, docs/*}`.
6. Финальное ревью, исправления одним проходом.
