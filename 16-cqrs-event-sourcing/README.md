# 16-cqrs-event-sourcing — состояние Forge = журнал событий

Продолжение 15 без CDC (Ordering → Forge через `es.forge.*`). В Forge наряд (**WorkOrder**) и склад
(**Material**) больше не строки, которые перезаписываются, а потоки событий в собственном **event store** на
PostgreSQL (`forge.event`, только добавление). Команды (`request`, `start`, `complete`) читают поток, решают
(`decide`) и дописывают события с проверкой версии. Чтение — отдельные модели (**CQRS**): `workshop-board`,
`smith-load`, `stock-ledger`, их строят **проекции** по журналу, каждая со своим checkpoint. Тот же журнал служит
outbox: `ForgeEvent` в Kafka публикует читатель журнала, а не транзакция команды.

| Что | Где |
|---|---|
| Журнал, конкуренция, глобальный порядок | `forge.event`, `JdbcEventStore`, [ADR-0017](../docs/adr/0017-own-event-store-on-postgresql.md), [ADR-0018](../docs/adr/0018-global-order-by-transaction-id.md) |
| Агрегаты decide/evolve | `domain/workorder`, `domain/material` |
| Проекции, rebuild, статус | `application/projection`, `GET /admin/projections`, `POST /admin/projections/{name}/rebuild` |
| Upcasting (`WorkOrderStarted` v1 → v2) | `EventCodec`, `WorkOrderStartedV1ToV2` |
| Склад на момент времени | `GET /api/stock/{material}?at=…` |
| Журнал как outbox | `OutcomePublisher`, [ADR-0019](../docs/adr/0019-journal-as-outbox.md) |

**Главное для собеседования:** [`scripts/drills.sh`](scripts/drills.sh) — гонка двух кузнецов за наряд,
перестройка read model с контрольной суммой, поздно включённая проекция, `kill -9` Forge с неотправленными
исходами, лаг проекций. Таблица ожиданий — в [learning-notes](docs/learning-notes.md).

**Документы:** [start](docs/start.md) — команды; [architecture](docs/architecture.md) — потоки записи и чтения,
таблицы, платформа; [learning-notes](docs/learning-notes.md) — учения и выводы (snapshots, GDPR, когда ES не нужен).
