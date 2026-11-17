# Start — 06-service-communication

Требования: JDK 25, Docker. Скрипты — bash (Git Bash / WSL).

```bash
# Сборка и все тесты (Testcontainers PostgreSQL, заглушки соседей на JDK HttpServer)
./mvnw -f 06-service-communication/pom.xml spotless:apply verify

# Весь стек в Compose + проверки через Gateway (собирает Jib-образы)
06-service-communication/scripts/e2e.sh
KEEP_STACK=true 06-service-communication/scripts/e2e.sh     # оставить стек поднятым
docker compose -p hephaestus-06 -f 06-service-communication/compose.yaml down -v
```

Руками (стек поднят, Gateway на `localhost:8080`):
```bash
curl -i localhost:8080/api/stock/ANVIL-01                       # v1: available + Deprecation/Sunset
curl -s -H 'API-Version: 2' localhost:8080/api/stock/ANVIL-01   # v2
curl -i -H 'Content-Type: application/json' -d '{"lines":[{"sku":"ANVIL-01","quantity":2}]}' localhost:8080/api/orders
curl -i -H 'Content-Type: application/json' -d '{"lines":[{"sku":"TONGS-01","quantity":1}]}' localhost:8080/api/orders  # 409
docker compose -p hephaestus-06 -f 06-service-communication/compose.yaml stop inventory   # then POST an order: 503 + Retry-After
```

Сервисы по отдельности (из IDE): Inventory `:8081`, Ordering `:8082` (`INVENTORY_URL`), Gateway `:8080`
(`ORDERING_URL`, `INVENTORY_URL`); базы — свои PostgreSQL (`SPRING_DATASOURCE_*`).

## На платформе (05)

1. После merge в `main` CI публикует образы `ghcr.io/cucumber405d/hephaestus/service-communication-{inventory,ordering,gateway}:sha-<7>`
   (тег — первые 7 символов коммита merge). Сделать три пакета публичными (Package settings → Change visibility).
2. В `06-service-communication/k8s/kustomization.yaml` заменить `pending-first-publish` на `sha-<7>`;
   в `platform/apps/values-minimal.yaml` и `values-full.yaml` — `serviceCommunication: true`. Commit, push в `main`.
3. Argo CD развернёт приложение `service-communication` в namespace `hephaestus-06`:
   ```bash
   kubectl -n argocd get application service-communication
   curl -s -H 'API-Version: 2' http://shop.localtest.me/api/stock/ANVIL-01
   ```
