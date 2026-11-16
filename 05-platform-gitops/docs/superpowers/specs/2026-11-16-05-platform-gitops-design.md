# 05-platform-gitops — спецификация

- **Статус:** утверждена (Dmitry: «следующая стадия», режим «всё сразу» — решения исполнителя перечислены в §2)
- **Дата:** 2026-11-16
- **Корневая спека:** [../../../../docs/superpowers/specs/2026-11-05-hephaestus-root-design.md](../../../../docs/superpowers/specs/2026-11-05-hephaestus-root-design.md)

## 1. Цель

Общая платформа `platform/` в одном kind-кластере, которой управляет **Argo CD по GitOps**: всё, кроме самого
Argo CD, ставится из Git через App-of-Apps. На платформе — то, что понадобится подпроектам 06–12: Gateway API
(вход), CloudNativePG (PostgreSQL), Strimzi (Kafka), Keycloak, Redis, стек наблюдаемости. Демонстрация — Catalog
из 03 на CloudNativePG за Gateway, с порядком «БД → миграция → API» через sync waves вместо `deploy.sh` из 04.

**Критерии успеха**
- `platform/bootstrap.sh` на чистой машине (Docker, kind, kubectl, helm) поднимает кластер и Argo CD; Argo CD сам
  доводит платформу до `Synced/Healthy`.
- `05-platform-gitops/scripts/smoke.sh`: все Application `Synced`+`Healthy`; через Gateway на `http://*.localtest.me`
  отвечают Catalog (3 категории), Grafana, Argo CD; в профиле `full` — Keycloak, Kafka `Ready`, Redis `PONG`.
- Профили `minimal` (CI, ноутбук) и `full` (всё); компоненты включаются флагами.
- CI на PR: kind + bootstrap `minimal` на ветке PR + smoke.

**Не цели:** сервисы 06+ (они придут на платформу своими Application); TLS/cert-manager (21); Istio (19);
Argo Rollouts (20); production-настройки компонентов (HA, бэкапы, мониторинг операторов).
**Анти-цели:** ручные `kubectl apply` после bootstrap; плавающие версии чартов и образов.

## 2. Решения

| Тема | Решение | Отклонено |
|---|---|---|
| Bootstrap | `helm install` Argo CD один раз, дальше Argo CD управляет и собой (Application `argocd`, multi-source: чарт + values из Git) | Только `kubectl apply` upstream-манифестов; Argo CD вне GitOps |
| App-of-Apps | Корневое Application `platform` → Helm-чарт `platform/apps`, шаблон на компонент; `targetRevision` и профиль — values, поэтому ветка PR разворачивается целиком из своей ветки | Статические Application-YAML (ревизия зашита в каждом) |
| Профили | `values-minimal.yaml` (argocd, gateway, cnpg, observability, catalog), `values-full.yaml` (+ strimzi/kafka, keycloak, redis) | Один профиль «всё» (тяжело для CI и ноутбука) |
| Gateway API | **Envoy Gateway** 1.8.3 (чарт ставит CRD Gateway API); `EnvoyProxy` → Service NodePort 30080 → kind `extraPortMappings` на `localhost:80`; хосты `*.localtest.me` (резолвятся в 127.0.0.1) | Ingress-NGINX (Ingress, не Gateway API); Istio (19); LoadBalancer через cloud-provider-kind |
| PostgreSQL | Оператор **CloudNativePG** (чарт 0.29.0); у каждого потребителя свой `Cluster` (catalog-db, keycloak-db) | Общий Postgres на всех; StatefulSet из 04 |
| Kafka | **Strimzi** 1.2.0, API `kafka.strimzi.io/v1`, KRaft, один узел (`KafkaNodePool` dual-role), эфемерное хранилище | ZooKeeper (удалён); 3 брокера |
| Keycloak | **Keycloak Operator** 26.8.1 (kustomize из `keycloak/keycloak-k8s-resources`), `Keycloak` CR на `keycloak-db`, HTTP за Gateway | Bitnami-чарт (образы Bitnami больше не бесплатны); `start-dev` |
| Redis | `StatefulSet` `redis:8` (1 реплика, без пароля, только внутри кластера) | Bitnami; оператор (YAGNI до 08) |
| Наблюдаемость | **grafana/otel-lgtm** 0.30.0 — Collector + Prometheus + Loki + Tempo + Pyroscope + Grafana в одном поде; OTLP `lgtm.observability:4317/4318` | Отдельные чарты LGTM (углубление — 11) |
| Catalog-демо | Образ 03 `sha-d730159`; CNPG `catalog-db` (wave 0) → Job миграции как Argo **Sync hook** (wave 1, `BeforeHookCreation`) → Deployment (wave 2) → HTTPRoute | `deploy.sh` (04); миграция при старте |
| Синхронизация | `automated` + `prune` + `selfHeal`, `ServerSideApply=true` (большие CRD), `CreateNamespace=true`, retry | Ручная синхронизация |
| Кластер | `kind` `hephaestus`: control-plane + 2 worker | 1 узел |

## 3. Структура

```
platform/
├── README.md, bootstrap.sh, down.sh
├── kind/cluster.yaml
├── argocd/values.yaml
├── apps/                    Helm-чарт App-of-Apps: values.yaml, values-minimal.yaml, values-full.yaml, templates/
└── components/
    ├── gateway/             GatewayClass, EnvoyProxy, Gateway, HTTPRoute argocd
    ├── observability/       otel-lgtm + HTTPRoute grafana
    ├── catalog/             catalog-db, migration hook, Deployment, Service, HTTPRoute
    ├── kafka/               KafkaNodePool, Kafka, KafkaTopic
    ├── keycloak/            keycloak-db, Keycloak, HTTPRoute
    └── redis/               StatefulSet, Service
05-platform-gitops/
├── README.md, docs/ (start, architecture, learning-notes, superpowers/)
└── scripts/smoke.sh
```

## 4. Проверки

| Что | Как |
|---|---|
| Рендер App-of-Apps | `helm template platform/apps -f values-<profile>.yaml` (в CI, перед кластером) |
| Платформа | `smoke.sh`: ждёт все Application `Synced`+`Healthy` (до 15 мин), затем HTTP через Gateway (`curl --resolve <host>:80:127.0.0.1`) |
| Catalog | 3 категории, POST 201; Job-хук завершён; `catalog-db` — CNPG `Cluster` в состоянии healthy |
| full | Keycloak `/realms/master/.well-known/openid-configuration` 200; `kafka/platform` `Ready`; `redis-cli PING` |
| CI | `05-platform-gitops.yml`: kind (`platform/kind/cluster.yaml`) → `bootstrap.sh minimal <ветка PR>` → smoke |

## 5. Learning-notes

GitOps (pull vs push, desired state в Git, drift и self-heal); Argo CD: Application, AppProject, App-of-Apps,
multi-source, sync waves и хуки, health checks, prune, ServerSideApply; Helm как пакет vs Kustomize как оверлей;
операторы и CRD (reconcile loop) на примере CNPG/Strimzi/Keycloak; Gateway API vs Ingress (GatewayClass/Gateway/
HTTPRoute, роли); CNPG: `Cluster`, сервисы `-rw/-ro/-r`, секрет `-app`; Strimzi: KRaft, node pools, v1 API;
Keycloak Operator; otel-lgtm и что будет в 11; ресурсы платформы на ноутбуке; вопросы для собеседования.

## 6. Риски

| Риск | Мера |
|---|---|
| Версии/поля CRD (Strimzi v1, Keycloak CR, EnvoyProxy patch) не проверены на кластере | `full` не в CI — проверить локально; ошибки видны в Argo CD UI, правка в Git |
| Порт 80 на Windows занят | Переопределение в `kind/cluster.yaml` (hostPort) + `--resolve` с портом |
| Argo CD тянет из GitHub — ветка должна быть запушена | bootstrap принимает ревизию; CI передаёт ветку PR |
| Ресурсы `full` | ~8–10 ГБ RAM; у Dmitry 30 ГБ на Docker |
| Health CNPG `Cluster` в Argo CD | Встроенная health-проверка Argo CD для `postgresql.cnpg.io/Cluster`; если нет — `resource.customizations` в values |
