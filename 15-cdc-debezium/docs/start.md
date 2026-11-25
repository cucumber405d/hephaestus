# 15 — запуск

## Что нужно

JDK 25, Docker, curl, python3; для платформы — kind, kubectl. Pact-плагин для gRPC (как в 14):

```bash
15-cdc-debezium/scripts/install-pact-plugin.sh
```

## Сборка и тесты

```bash
./mvnw -f 15-cdc-debezium/pom.xml clean spotless:apply verify
git status 15-cdc-debezium/pacts                      # изменился — закоммитить
```

`OutboxBytesIT` — побайтное совпадение outbox и relay; `OutboxRegistryDownIT` — реестр недоступен → 503, ничего
не сохранено.

## End to end в Docker Compose (то же, что CI)

```bash
15-cdc-debezium/scripts/e2e.sh                        # OUTBOX_MODE=debezium (по умолчанию)
OUTBOX_MODE=modulith 15-cdc-debezium/scripts/e2e.sh   # relay из 14
```

Порты при `KEEP_STACK=true`: ordering 18080, forge 18081, supplier 18082, Kafka Connect 18083, Apicurio 18085,
PostgreSQL Ordering 15432. Коннектор: `curl localhost:18083/connectors/ordering-outbox/status`.

## Учения отказов

```bash
15-cdc-debezium/scripts/test/reconcile-fixture.sh     # сверка без Docker
15-cdc-debezium/scripts/drills.sh all debezium        # 1–5
15-cdc-debezium/scripts/drills.sh all modulith        # 1, 2, 5 (3 и 4 — только про Connect)
15-cdc-debezium/scripts/drills.sh 3 debezium          # одно учение
```

Каждое учение на свежем стенде (образы — из `e2e.sh` или `BUILD=true`). Таблица — `target/drills/results.md`.

## Платформа (kind)

```bash
platform/bootstrap.sh scaling
15-cdc-debezium/scripts/deploy-local.sh               # образы отсюда: jib + docker build connect/, kind load
15-cdc-debezium/scripts/live.sh                       # заказ → Debezium → Forge → READY через cdc.localtest.me
```

Через Argo CD — профили `scaling` и `full` (флаг `cdcDebezium`), образы `sha-2de88df` из GHCR:
`platform/bootstrap.sh scaling`, затем `scripts/live.sh`.
