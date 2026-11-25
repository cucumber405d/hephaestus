# 15-cdc-debezium — доставку отдаёт база

Продолжение 14 (Ordering → Forge). В 10, 13 и 14 outbox устроен на Spring Modulith: событие пишется в транзакции, а
отправляет его в Kafka само приложение после коммита. Здесь Ordering только пишет готовые байты Avro в таблицу
`ordering.outbox`, а **Debezium** (Kafka Connect, PostgreSQL connector, Outbox Event Router) читает WAL и публикует
их без изменений. Forge не знает, кто положил сообщение в топик: байты те же, что у relay из 14 (`OutboxBytesIT`).

| Режим (`OUTBOX_MODE`) | Кто отправляет | Как в |
|---|---|---|
| `debezium` (по умолчанию) | Kafka Connect по WAL | 15 |
| `modulith` | `RequestRelay` в процессе Ordering | 10, 13, 14 |

**Главное для собеседования:** [`scripts/drills.sh`](scripts/drills.sh) — пять отказов против обоих режимов, у
каждого проверка «отказ действительно случился» и сверка «каждый принятый заказ → ровно один наряд». Таблица — в
[learning-notes](docs/learning-notes.md).

Платформа: CNPG с `wal_level: logical` и ролью `cdc`, Strimzi `KafkaConnect` + `KafkaConnector` как YAML под Argo CD,
Apicurio в `hephaestus-15`, топики `cdc.forge.*`.

**Решения:** [ADR-0015](../docs/adr/0015-outbox-delivery-by-cdc.md) (доставка через CDC),
[ADR-0016](../docs/adr/0016-outbox-carries-avro-bytes.md) (в outbox готовые байты Avro).

**Документы:** [start](docs/start.md) — команды; [architecture](docs/architecture.md) — поток, режимы, платформа;
[learning-notes](docs/learning-notes.md) — учения и выводы.
