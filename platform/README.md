# platform — общий кластер Hephaestus

Один kind-кластер `hephaestus` для подпроектов 05+, которым управляет Argo CD из этого репозитория.
Как устроено и почему — [05-platform-gitops](../05-platform-gitops/README.md).

```bash
platform/bootstrap.sh minimal            # или: full; третий аргумент — ветка Git (по умолчанию main)
05-platform-gitops/scripts/smoke.sh minimal
platform/down.sh
```

| Путь | Что |
|---|---|
| `kind/cluster.yaml` | кластер: control-plane + 2 worker, `localhost:80` → Gateway |
| `bootstrap.sh` | kind → Argo CD (Helm) → корневое Application `platform` |
| `argocd/values.yaml` | настройки Argo CD (им же и применяются) |
| `apps/` | Helm-чарт App-of-Apps: Application на компонент, профили `values-minimal.yaml` / `values-full.yaml` |
| `components/` | манифесты компонентов: gateway, observability, catalog, kafka, keycloak, redis |

Адреса: http://argocd.localtest.me, http://grafana.localtest.me (admin/admin), http://catalog.localtest.me,
http://keycloak.localtest.me (full). Внутри кластера: OTLP `lgtm.observability:4317/4318`, Kafka
`platform-kafka-bootstrap.kafka:9092`, Redis `redis.redis:6379`, PostgreSQL — свой CNPG `Cluster` у каждого сервиса.
