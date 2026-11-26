# 16 — архитектура

## Сервисы

Как в 15, без Kafka Connect: **Ordering** (заказы, gRPC-котировка у Forge, outbox Spring Modulith → `es.forge.requests`),
**forge-service** (наряды и склад, event-sourced), **supplier-stub** (поставщик материала), Kafka, Apicurio,
два PostgreSQL. Между сервисами — Avro (`WorkOrderRequest`, `ForgeEvent`), как в 14–15.

## Запись (command side)

```
es.forge.requests ─► WorkOrderIntake ─► WorkOrders.request ─► WorkOrderRepository.load(FW-<orderId>)
                                                              │  fold(events) → WorkOrderState, version
                                                              ▼
                                         WorkOrderDecider.decide(command, state) → events
                                                              ▼
                                         EventStore.append(stream, expectedVersion, events)  ── forge.event
```

- **Поток** (stream) = агрегат: `FW-<orderId>` (тип `WorkOrder`), `material-<code>` (тип `Material`).
- **decide / evolve**: `decide(command, state) → List<event>` (или `WorkOrderRefused`), `evolve(state, event) → state`;
  `fold` = `evolve` по всем событиям. Домен — чистая Java, без Spring и SQL.
- **Оптимистичная конкуренция**: `unique(stream_id, version)`. Второй писатель с той же ожидаемой версией получает
  `ConcurrencyConflict` → HTTP 409; Kafka-intake повторяет до 3 раз (перечитать поток и решить заново).
- **Статусы наряда**: `WAITING_FOR_MATERIAL → ALLOCATED → IN_PROGRESS → DONE`, или `FAILED` с причиной.
- **Склад**: `StockReceived` (INITIAL/SUPPLIER), `StockTaken(workOrderId)`; нехватка → `InsufficientStock`, наряд ждёт
  поставку (`SupplyRetryScheduler`).
- **Идемпотентность**: повторная доставка запроса с тем же orderId попадает в тот же поток, `decide` на `Request`
  для существующего наряда не даёт событий.

## Журнал `forge.event`

| Колонка | Смысл |
|---|---|
| `global_position` | `bigserial`, порядок вставки (не коммита!) |
| `stream_id`, `stream_type`, `version` | поток и номер события в нём |
| `type`, `schema_version`, `payload jsonb` | событие и версия его схемы (upcasting при чтении) |
| `metadata jsonb` | `correlationId` = orderId |
| `recorded_at` | `clock_timestamp()` |
| `tx_id xid8` | `pg_current_xact_id()` — для глобального чтения |

Триггер запрещает `update`/`delete`/`truncate`. Глобальное чтение — `(tx_id, global_position) > checkpoint` и
`tx_id < pg_snapshot_xmin(pg_current_snapshot())`: читатель не перепрыгнет событие ещё открытой транзакции
([ADR-0018](../../docs/adr/0018-global-order-by-transaction-id.md)).

## Чтение (query side)

| Проекция | Таблица | Кто читает |
|---|---|---|
| `workshop-board` | `read.workshop_board` | `GET /api/work-orders?status=`, повтор поставки |
| `smith-load` | `read.smith_load` (+ `read.smith_assignment`) | учения, отчёты |
| `stock-ledger` | `read.stock_ledger`, `read.stock_movement` | `GET /api/stock/{material}` |

`ProjectionRunner` (каждые `PROJECTIONS_POLL_INTERVAL`, по умолчанию 200 ms): для каждой включённой проекции одна
транзакция — `select … for update` строки checkpoint, батч из журнала (`PROJECTIONS_BATCH_SIZE`, 500), применить,
сохранить checkpoint. Ошибка — откат батча, checkpoint на месте, `lastError` в `/admin/projections`, остальные
проекции продолжают. Выключение: `PROJECTIONS_<NAME>_ENABLED=false` (например `PROJECTIONS_SMITH_LOAD_ENABLED`).
**Rebuild**: под той же блокировкой `reset()` таблицы и checkpoint в начало — проекция догоняет заново.

Склад «на момент» (`?at=`) — не проекция, а свёртка потока `material-<code>` до `recorded_at <= at`.

## Журнал как outbox

`OutcomePublisher` — такой же читатель журнала со своим checkpoint `forge-events-publisher`. Для
`WorkOrderCompleted`/`WorkOrderFailed` шлёт `ForgeEvent` в `es.forge.events` (key = orderId), ждёт ack и только потом
двигает checkpoint. `eventId` = UUID от `stream/version` — повтор после сбоя несёт тот же id, inbox Ordering
отбрасывает дубль ([ADR-0019](../../docs/adr/0019-journal-as-outbox.md)). Исторический fixture `V5` не публикуется:
миграция ставит checkpoint publisher на голову журнала.

## Миграции Forge

`V3__event_store` (журнал, checkpoint), `V4__read_models`, `V5__journal_seed` (склад 20/20, три исторических наряда
с `WorkOrderStarted` v1), `V6__drop_tables` (старые `work_order`, `material` из 15).

## Платформа

Namespace `hephaestus-16`, Argo app `cqrs-event-sourcing` (флаг `cqrsEventSourcing`), CNPG `ordering-db` и
`forge-db` (без логической репликации), топики `es-forge-*` в Strimzi, Apicurio, HTTPRoute `es.localtest.me`:
`/api/work-orders`, `/api/stock`, `GET /admin/projections` → forge-service, остальное → Ordering.
