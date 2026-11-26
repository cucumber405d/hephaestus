# 16 — запуск

## Что нужно

JDK 25, Docker, curl, python3; для платформы — kind, kubectl. Pact-плагин для gRPC (как в 14):

```bash
16-cqrs-event-sourcing/scripts/install-pact-plugin.sh
```

## Сборка и тесты

```bash
./mvnw -f 16-cqrs-event-sourcing/pom.xml clean spotless:apply verify
git status 16-cqrs-event-sourcing/pacts               # изменился — закоммитить
```

Ключевые тесты Forge: `EventStoreIT` (конкуренция версий, append-only, порядок `(tx_id, position)`),
`WorkOrderDeciderTest`, `ProjectionRunnerIT` (откат батча, rebuild во время догонки), `ConcurrentStartIT`
(10 кузнецов → один 200), `TimeTravelIT`, `HistoricalFixtureIT` (upcasting v1), `ForgeEventsPublisherIT`.

## End to end в Docker Compose (то же, что CI)

```bash
16-cqrs-event-sourcing/scripts/e2e.sh                 # SKIP_BUILD=true, KEEP_STACK=true
```

Порты при `KEEP_STACK=true`: ordering 18080, forge 18081, supplier 18082, Apicurio 18085.

```bash
curl -s localhost:18081/admin/projections                         # checkpoint, lag, lastError
curl -s -X POST localhost:18081/admin/projections/workshop-board/rebuild
curl -s "localhost:18081/api/work-orders?status=ALLOCATED"
curl -s -X POST "localhost:18081/api/work-orders/FW-<orderId>/start?smith=anna"
curl -s "localhost:18081/api/stock/IRON-01?at=2026-11-26T10:00:00Z"
docker compose -f 16-cqrs-event-sourcing/compose.yaml -p hephaestus-16 exec postgres-forge \
  psql -U forge -d forge -c "select global_position, stream_id, version, type from forge.event order by 1"
```

## Учения

```bash
16-cqrs-event-sourcing/scripts/test/reconcile-fixture.sh   # сверка без Docker
16-cqrs-event-sourcing/scripts/drills.sh all               # 1–5, каждое на свежем стенде
16-cqrs-event-sourcing/scripts/drills.sh 3                 # одно учение
```

Образы — из `e2e.sh` или `BUILD=true`. Таблица — `target/drills/results.md`.

## Платформа (kind)

```bash
platform/bootstrap.sh scaling
16-cqrs-event-sourcing/scripts/deploy-local.sh             # образы отсюда: jib, kind load
16-cqrs-event-sourcing/scripts/live.sh                     # заказ → журнал → кузнец live → READY, проекции lag 0
```

Через Argo CD — флаг `cqrsEventSourcing` (пока `false`; включается отдельным PR после публикации образов).
