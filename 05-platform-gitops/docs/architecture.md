# Architecture — 05-platform-gitops

## Bootstrap и App-of-Apps

```mermaid
flowchart TB
    dev[bootstrap.sh] -->|helm install| argo[Argo CD]
    dev -->|kubectl apply| root[Application platform<br/>Helm chart platform/apps]
    git[(GitHub<br/>cucumber405d/hephaestus)] -->|pull| argo
    argo --> root
    root -->|wave -3| a0[argocd<br/>self-managed]
    root -->|wave -2| a1[envoy-gateway] & a2[cnpg] & a3[strimzi] & a4[keycloak-operator]
    root -->|wave -1| a5[gateway<br/>GatewayClass, Gateway]
    root -->|wave 0| a6[observability] & a7[catalog] & a8[kafka] & a9[keycloak] & a10[redis]
```

Профиль `minimal`: argocd, envoy-gateway, gateway, cnpg, observability, catalog. `full`: все.

## Трафик

```mermaid
flowchart LR
    browser[браузер / curl<br/>*.localtest.me = 127.0.0.1] -->|:80| kind[kind control-plane<br/>hostPort 80 -> NodePort 30080]
    kind --> envoy[Envoy proxy<br/>Gateway platform]
    envoy -->|HTTPRoute argocd| argosrv[argocd-server]
    envoy -->|HTTPRoute grafana| lgtm[otel-lgtm :3000]
    envoy -->|HTTPRoute catalog| cat[catalog pods]
    envoy -->|HTTPRoute keycloak| kc[keycloak-service :8080]
    cat --> cdb[(CNPG catalog-db-rw)]
    kc --> kdb[(CNPG keycloak-db-rw)]
```

## Catalog: порядок через волны

| Волна | Объект | Argo CD ждёт |
|---|---|---|
| 0 | `Cluster catalog-db` (CNPG) | health «Cluster in healthy state» |
| 1 | `Job catalog-migrate` — Sync hook, `BeforeHookCreation` | Job complete |
| 2 | Deployment, Service, HTTPRoute | rollout, readiness |

## Где что живёт

| Namespace | Содержимое |
|---|---|
| `argocd` | Argo CD, Application, HTTPRoute argocd |
| `envoy-gateway-system` | контроллер Envoy Gateway, EnvoyProxy, Gateway, прокси Envoy |
| `cnpg-system`, `strimzi`, `keycloak` | операторы |
| `observability` | otel-lgtm |
| `catalog` | catalog-db, Job, API |
| `kafka`, `keycloak`, `redis` | Kafka `platform`, Keycloak + keycloak-db, Redis |

Решения и альтернативы — [спецификация](superpowers/specs/2026-11-16-05-platform-gitops-design.md), раздел 2.
