# 15-cdc-debezium — design

## Зачем

В 10, 13 и 14 outbox устроен на Spring Modulith: событие пишется в транзакции, а отправляет его в Kafka само
приложение после коммита (в 14 — `RequestRelay`). Слабые места: отправка живёт в процессе сервиса, а неотправленное
событие повторяется только после рестарта сервиса. 15 переносит доставку из приложения в базу: Debezium читает WAL
PostgreSQL и публикует строки outbox-таблицы сам. Сравнение с подходом 10 делается на одном потоке, сценариями отказов.

Решения Dmitry: продолжение 14 (поток Ordering → Forge, вариант B); в outbox лежат готовые байты Avro, Kafka Connect
кладёт их как есть (вариант 1); 15 живёт в Compose **и** на платформе (kind, Strimzi KafkaConnect).

## Цели / не-цели / анти-цели

- **Цели**
  - Ordering пишет `WorkOrderRequest` (контракт 14, Avro через Apicurio) в outbox-таблицу в транзакции заказа;
    Debezium (PostgreSQL connector + Outbox Event Router) публикует его в `cdc.forge.requests`.
  - Режим доставки — настройка запуска `OUTBOX_MODE=modulith|debezium`: `RequestRelay` из 14 остаётся для сравнения.
  - **Учения отказов** (`scripts/drills.sh <drill> <mode>`) в Compose — одна таблица «отказ → что видит клиент → как
    восстановилось» для обоих режимов:
    1. Kafka недоступна 30 с во время заказов.
    2. Ordering убит (`kill -9`) сразу после приёма заказов.
    3. Kafka Connect остановлен на 60 с (только debezium): рост удерживаемого WAL (`pg_replication_slots`), затем
       догоняет.
    4. Рестарт Connect → повторная доставка (at-least-once) → Forge всё равно создаёт ровно один наряд.
    5. Задержка «коммит → запись в топике», p50/p95, оба режима.
  - Итог каждого учения — сверка «каждый принятый заказ на изделие → ровно один наряд в Forge» (как reconcile в 13).
  - **Как учения 1 и 2 судят режим modulith (решение Dmitry: вариант A).** После восстановления учение ждёт столько
    же, сколько в режиме debezium, и проверяет, что заказы **застряли** без нарядов (слабость видна и проверена);
    затем перезапускает Ordering (`republish-outstanding-events-on-restart` из 14) — после этого сверка обязана пройти.
    В таблице: modulith — «восстановилось только после рестарта сервиса», debezium — «само». Relay из 14 не меняется
    (никакого повтора по расписанию): сравнивается именно подход 10. CI зелёный в обоих режимах.
  - Платформа: CNPG с логической репликацией, Strimzi `KafkaConnect` + `KafkaConnector` как YAML под Argo CD,
    Apicurio и Job регистрации схем в namespace `hephaestus-15`.
- **Не-цели**: Debezium для `forge.events` (Forge оставляет relay из 14), Debezium Server и embedded engine,
  CDC таблиц предметной области (только outbox), Pact Broker, KEDA/масштабирование.
- **Анти-цели**: сообщение от Debezium, которое отличается от сообщения `RequestRelay` хоть одним байтом значения;
  учение, которое «проходит», не устроив отказ (проверка, что отказ действительно случился, — часть учения).

## Состав

```
15-cdc-debezium/
├── contracts/      ← копия 14 (версия 1.1.0, released/1.0.0), топики cdc.*
├── ordering/       ← из 14 + outbox-таблица и режим debezium
├── forge-service/  ← из 14, без изменений поведения (топики cdc.*)
├── supplier-stub/  ← копия
├── connect/        ← образ Kafka Connect с плагином Debezium PostgreSQL (Dockerfile), конфиг коннектора
├── scripts/        ← e2e.sh, drills.sh, deploy-local.sh, register-schemas.sh
├── compose.yaml
└── k8s/            ← как 13: app/ (CNPG, Apicurio, Connect, сервисы, route), topics.yaml, overlays/local
```

## Как устроено (решения, мои — помечены)

- **Outbox-таблица** `ordering.outbox(id uuid, aggregate_type, aggregate_id, type, payload bytea, created_at)` —
  схема Event Router. `payload` = байты, которые выдаёт `AvroKafkaSerializer` Apicurio (с явной версией схемы
  `@contracts.version@`), т.е. то же, что `RequestRelay` отправил бы. Connect: `value.converter=ByteArrayConverter`,
  `key` = `aggregate_id` (id заказа), топик = `cdc.forge.requests` (route по `aggregate_type`). Сериализация в
  транзакции заказа: реестр недоступен → заказ не принят (моё: честнее, чем принять и не суметь отправить).
- **Заголовки.** Сериализатор Apicurio может класть id схемы в заголовки, а не в payload. Значение должно нести id
  в теле (как в 14, `headers` выключены), иначе байты в outbox неполные — строитель проверяет настройку.
- **Чистка outbox** (моё): строки удаляются задачей по расписанию старше 1 часа — таблицу можно посмотреть руками на
  демо; Debezium читает WAL, а не таблицу.
- **Режим** `OUTBOX_MODE` читается при старте; в режиме debezium `RequestRelay` не создаётся, в режиме modulith
  outbox-таблица не пишется.
- **Forge** не знает о режиме: idempotent по order id, как в 13/14.
- **Платформа** (мои): CNPG `postgresql.parameters.wal_level: logical`, отдельная роль с `REPLICATION` (managed roles),
  публикация только на `ordering.outbox`; образ Connect собирается в CI (FROM Strimzi Kafka image + плагин
  Debezium), публикуется в GHCR, локально — `kind load`; Apicurio — Deployment in-memory + Job регистрации схем
  (sync-wave до сервисов); топики `cdc.forge.requests`, `cdc.forge.events` — KafkaTopic в namespace kafka.
  Приложение платформы `cdcDebezium` включено в профилях scaling и full после первой публикации образов.
- **«Байты одинаковые»** (моё, после проверки строителем) — интеграционный тест: одно и то же событие кодируется
  путём relay и путём outbox, значения сравниваются побайтно, включая префикс id схемы. Оба режима пишут в один топик
  `cdc.forge.requests` (иначе artifact и id схемы разные).
- Учения 3 и 4 — в Compose; на kind работает только режим debezium: поток и сверка.
- Strimzi в платформе следит за всеми namespace (`watchAnyNamespace: true`) — `KafkaConnect` живёт в `hephaestus-15`.

## Версии

Как в 14 (Boot 4.1.x, Modulith 2.1.2, Avro 1.12.2, Apicurio 3.3.5, gRPC из Boot, Pact 4.7.5); Debezium — последняя
стабильная 3.x (строитель проверяет), Strimzi 1.2.0 (платформа), образ Connect — на Kafka той версии, что
поддерживает Strimzi 1.2.0.

## Решения → ADR

- ADR-0015 — доставка outbox через CDC (Debezium) против публикации из приложения; at-least-once и идемпотентность.
- ADR-0016 — в outbox готовые байты Avro, Connect не конвертирует (контракт 14 важнее «Debezium по учебнику»).

## Документация и CI

README, `docs/{start,architecture,learning-notes}.md` (таблица учений с реальными цифрами, WAL и replication slot как
риск, сравнение с 10). CI: verify, Compose e2e (оба режима), drills 1–5, сборка и публикация образов (ordering,
forge-service, supplier-stub, connect) на main; kustomize render.

## За строителем

Точная версия Debezium и Strimzi-образа Kafka, SMT-конфиг Event Router, как Apicurio-сериализатор вызывается вне
Kafka-продюсера, способ «убить» Kafka/Connect в Compose, измерение задержки, разбиение на задачи.
