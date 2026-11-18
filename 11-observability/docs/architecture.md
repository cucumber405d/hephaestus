# 11-observability — архитектура

```
             OTLP (traces, metrics, logs)                       ┌───────────── lgtm ─────────────┐
 ordering ─┐  Boot: Micrometer → OTel SDK                       │ OTel Collector :4317/:4318      │
 payment  ─┼──────────────────────────────────────────────────▶ │  ├─ Tempo :3200   (traces)      │
 inventory─┘                                                    │  ├─ Prometheus :9090 (metrics)  │
 delivery ──── Java agent (OTEL_* env) ───────────────────────▶ │  ├─ Loki :3100    (logs)        │
                                                                │  └─ Grafana :3000 (provisioned) │
                                                                └─────────────────────────────────┘
```

Бизнес-часть — как в 10 ([архитектура 10](../../10-outbox-saga/docs/architecture.md)); ниже — только то, что
добавлено.

## Как трейс проходит сагу

- **HTTP → Ordering:** наблюдение Spring MVC продолжает входящий `traceparent` или начинает трейс.
- **Outbox:** `Outbox.send` (внутри бизнес-транзакции) сохраняет текущий контекст `TraceCarrier.capture()` в
  `OutgoingMessage.traceparent`; Modulith кладёт событие в `event_publication` вместе с ним.
- **Отправка:** `OutboxRelay` — слушатель после коммита (как экстернализатор Modulith), который возвращает отправку
  как `CompletableFuture`: Modulith отмечает публикацию завершённой, когда future успешен. Сама отправка — в одном
  потоке `outbox-relay` по порядку публикаций (сообщения одного заказа уходят в порядке записи, и после сбоя тоже;
  соединение с БД не держится, пока Kafka медлит). Отправка — дочерний спан `outbox relay` **сохранённого**
  контекста; `KafkaTemplate` с `observation-enabled` пишет `traceparent` в заголовок записи. Сбой Kafka → future
  с ошибкой → публикация остаётся незавершённой → `OutboxResubmitter` (каждые 10 s) вызывает relay снова — и снова
  с сохранённым контекстом. Спан запроса и спан отправки помечены `order.id`: если outbox порвёт трейс, поиск по
  заказу найдёт два трейса.
- **Kafka → участник:** listener-контейнер с `observation-enabled` продолжает трейс из заголовка; слушатели
  ставят на спан `order.id`, `saga.style`, `message.type`.
- **Таймаут:** строка `saga` хранит `traceparent` последнего шага; `@Scheduled`-проверка для каждой просроченной
  саги открывает спан `saga timeout` с этим родителем — компенсации внутри трейса саги.
- **Delivery (агент):** агент инструментирует Kafka-клиент, Spring MVC, JDBC и пулы потоков; отправку outbox
  делает экстернализатор Modulith (как в 10). Первая отправка — в трейсе (контекст переносит пул потоков),
  переотправка — нет.
- Рубильник `observability.outbox.carry-trace` (`OUTBOX_CARRY_TRACE`): `false` — `capture()` возвращает `null`,
  `continueTrace` ничего не восстанавливает. Текущий контекст в поток отправки передаётся всё равно (как у
  экстернализатора «из коробки»), поэтому первая отправка остаётся в трейсе, а переотправка начинает новый.

## Метрики

| Метрика (Prometheus) | Где | Что |
|---|---|---|
| `saga_completed_total{style,outcome,reason}` | Ordering | заказы, дошедшие до финального статуса |
| `saga_duration_seconds_bucket{style}` | Ordering | от размещения до финала |
| `saga_step_duration_seconds_bucket{style,step}` | Ordering | от предыдущей записи истории до ответа участника |
| `saga_timeouts_total{step}` | Ordering | просроченные шаги оркестрации |
| `outbox_publications_incomplete` | Ordering, Payment, Inventory | неотправленные публикации |
| `http_server_requests_*`, JVM, Kafka-клиент | все | стандартные (у Delivery — от агента, по OTel-конвенциям) |

Экспорт: Micrometer OTLP registry, шаг 10 s, время в секундах. Алерт **Outbox backlog**:
`max by (job) (outbox_publications_incomplete) > 0` дольше 1 минуты.

## Логи

ECS JSON в stdout (`logging.structured.format.console`) и те же записи через OpenTelemetry Logback appender
(`logback-spring.xml`, `LogExport`) в Loki с trace id и span id. У Delivery — appender агента.

## Файлы

- `*/messaging/TraceCarrier.java`, `OutboxRelay.java`, `OutboxMetrics.java`, `LogExport.java` — одинаковый
  набор в Ordering, Payment, Inventory.
- `ordering/.../orders/SagaMetrics.java`; `saga.traceparent` в `V1__ordering.sql`.
- `delivery/pom.xml` — агент в образе (`/otel`, `-javaagent` в `JAVA_TOOL_OPTIONS`).
- `grafana/` — дашборд `saga.json`, провайдер дашбордов, правило алерта.
