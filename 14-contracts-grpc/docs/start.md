# 14 — запуск

## Что нужно

- JDK 25, Docker (Testcontainers, buf, AsyncAPI CLI), curl, python3.
- Pact-плагин для gRPC — это бинарник с GitHub, а не артефакт Maven Central. Ставится один раз (Linux, macOS, WSL2):

```bash
14-contracts-grpc/scripts/install-pact-plugin.sh        # версия 0.8.1; PACT_PROTOBUF_PLUGIN_VERSION=… — другая
```

## Сборка и тесты

```bash
./mvnw -f 14-contracts-grpc/pom.xml spotless:apply verify
git status 14-contracts-grpc/pacts                      # pact-файл пишет ordering; изменился — закоммитить
```

Порядок модулей важен: `ordering` пишет `pacts/ordering-forge-service-quote.json`, `forge-service` проверяет себя
по нему. `SchemaCompatibilityIT` (модуль `contracts`) поднимает Apicurio и проверяет текущие схемы против 1.0.0.

## Проверки контрактов

```bash
14-contracts-grpc/scripts/check-proto.sh                # buf breaking: quote.proto против released/1.0.0
14-contracts-grpc/scripts/check-asyncapi.sh             # AsyncAPI вместе со ссылками на .avsc
```

## Сценарии поломок

```bash
14-contracts-grpc/scripts/test/breaking-change-selftest.sh    # «патч не наложился» = выход 2, не «поймано»
14-contracts-grpc/scripts/breaking-change.sh all              # baseline зелёный, затем пять строк CAUGHT by …
14-contracts-grpc/scripts/breaking-change.sh 2                # один сценарий
```

Каждый сценарий работает на временной копии; рабочее дерево не меняется. После `spotless:apply`, который
переформатировал Java-код из патчей, сценарий может «дрейфовать» (выход 2) — тогда патч надо обновить.

## End to end в Docker Compose (то же, что CI)

```bash
14-contracts-grpc/scripts/e2e.sh                        # SKIP_BUILD=true, KEEP_STACK=true
```

Порты при `KEEP_STACK=true`: ordering 18080, forge-service 18081 (REST) / 9090 внутри сети, supplier 18082,
Apicurio 18085. Руками:

```bash
curl -s -X POST localhost:18080/api/orders -H 'Content-Type: application/json' \
  -d '{"customerId":"me","custom":true,"material":"IRON-01","quantity":5}'
curl -s localhost:18080/api/orders/<id>                 # ACCEPTED, priceCents 6000, leadTimeDays 3
curl -s localhost:18085/apis/registry/v3/groups/default/artifacts/forge.requests-value/versions
```
