---
status: accepted
date: 2026-11-26
decision-makers: Dmitry
scope: 16-cqrs-event-sourcing
---

# ADR-0017: Свой event store на PostgreSQL, без EventStoreDB/Axon

## Контекст и проблема

Наряды и склад Forge переходят на event sourcing. Нужен журнал с оптимистичной конкуренцией по потоку и
глобальным чтением для проекций. Цель подпроекта — понять механику, а не освоить продукт.

## Рассмотренные варианты

- **Своя таблица `forge.event` в PostgreSQL** (уже есть CNPG, Flyway, JdbcClient).
- EventStoreDB (Kurrent) — отдельный сервер, свой клиент, подписки из коробки.
- Axon Framework + Axon Server — фреймворк агрегатов, саг и проекций.

## Решение

Выбрана **своя таблица**: `unique(stream_id, version)` для конкуренции, `jsonb` payload с `schema_version`,
триггер append-only, checkpoint проекций в той же БД. Атомарность «события + checkpoint проекции» — одна локальная
транзакция.

### Последствия

- Хорошо: всё видно в SQL, без нового компонента платформы; транзакции общие с read models.
- Плохо: подписки, порядок, rebuild, upcasting — свой код и свои тесты (ADR-0018); масштаб ограничен одной БД.
- Переход на EventStoreDB возможен: порт `EventStore` в application-слое.
