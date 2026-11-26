---
status: accepted
date: 2026-11-26
decision-makers: Dmitry
scope: 16-cqrs-event-sourcing
---

# ADR-0019: Журнал событий как outbox — publisher со своим checkpoint

## Контекст и проблема

Ordering ждёт `ForgeEvent` (наряд выполнен / не выполнен). В 13–15 Forge писал состояние и outbox-запись в одной
транзакции (Spring Modulith). При event sourcing событие уже записано в журнал в транзакции команды.

## Рассмотренные варианты

- **Читатель журнала (`OutcomePublisher`) со своим checkpoint `forge-events-publisher`.**
- Оставить Spring Modulith outbox рядом с журналом — две записи одного факта.
- Debezium по WAL таблицы `forge.event` (как в 15) — новый компонент ради того, что журнал уже умеет.

## Решение

Выбран **publisher-читатель**: та же механика, что у проекций (строка checkpoint `for update`, батч, ADR-0018), но
вместо таблицы — Kafka. Каждый send ждёт ack, затем checkpoint сохраняется в той же транзакции; ошибка — откат,
повтор. `eventId` = `UUID.nameUUIDFromBytes(stream/version)` — стабилен между повторами. Rebuild publisher запрещён
(400): отправленное не вернуть. Исторический fixture не публикуется — миграция ставит checkpoint на голову журнала.
Порт `OutcomeSink` держит application-слой без Kafka (ArchUnit).

### Последствия

- Хорошо: одна запись факта; нет таблицы outbox и её очистки; отказ Kafka не мешает командам.
- Плохо: at-least-once — потребитель обязан дедуплицировать по `eventId` (inbox Ordering, уже с 13); задержка
  публикации = интервал опроса.
