# 11-observability — сага из 10, видимая насквозь

Подопытный — сага заказа из [10-outbox-saga](../10-outbox-saga/README.md): Ordering → Payment → Inventory → Delivery,
хореография и оркестрация, outbox. Здесь она становится **наблюдаемой**: **один заказ — один трейс** через HTTP,
Kafka и outbox во всех четырёх сервисах, метрики саги на дашборде, JSON-логи с trace id в Loki — всё в одном
контейнере `grafana/otel-lgtm` (Collector, Prometheus, Loki, Tempo, Grafana).

Два способа инструментирования **рядом, в одном трейсе**:

- Ordering, Payment, Inventory — средствами **Spring Boot 4** (`spring-boot-starter-opentelemetry`: Micrometer
  Observation + Tracing с мостом в OpenTelemetry, OTLP);
- Delivery — только **OpenTelemetry Java-агент**, в коде ни строчки телеметрии.

Главный опыт — **трейс через outbox**: сообщение уходит в Kafka после коммита, а после сбоя Kafka — через минуты из
планировщика. Контекст трейса хранится в самом сообщении, и даже переотправка продолжает трейс саги
(рубильник `OUTBOX_CARRY_TRACE=false` показывает разрыв).

**Чему учит:** распределённый трейсинг и W3C `traceparent`, перенос контекста через асинхронность и хранилище,
Micrometer vs Java-агент, предметные метрики и гистограммы, структурные логи и корреляция трейс ↔ лог ↔ метрика,
провижининг Grafana.

- Спецификация: [docs/superpowers/specs/2026-11-18-11-observability-design.md](docs/superpowers/specs/2026-11-18-11-observability-design.md)
- План: [docs/superpowers/plans/2026-11-18-11-observability.md](docs/superpowers/plans/2026-11-18-11-observability.md)
- Старт: [docs/start.md](docs/start.md)
- Архитектура: [docs/architecture.md](docs/architecture.md)
- Выводы: [docs/learning-notes.md](docs/learning-notes.md)
