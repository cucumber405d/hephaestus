# 09-events-kafka — события через Kafka

**Ordering** размещает заказ и публикует событие `OrderPlaced` в топик `orders.events` (3 партиции, ключ —
покупатель). **Notification** читает его и уведомляет покупателя — асинхронно, без прямого вызова.
Стенд — площадка для опытов руками: сбои по команде, масштабирование `--scale`, **Kafka UI**.

- ретраи с backoff → **DLT** → переотправка после починки;
- **идемпотентный consumer**: дубль события не даёт второго уведомления;
- порядок событий одного покупателя (ключ → партиция);
- **consumer group** и ребалансировка; **share group** (очереди Kafka 4.2) рядом для сравнения.

**Чему учит:** партиции, ключи и порядок; consumer groups; at-least-once и идемпотентность; ретраи против DLT;
poison message; dual write (мостик к outbox в 10); share groups против consumer groups.

- Спецификация: [docs/superpowers/specs/2026-11-18-09-events-kafka-design.md](docs/superpowers/specs/2026-11-18-09-events-kafka-design.md)
- План: [docs/superpowers/plans/2026-11-18-09-events-kafka.md](docs/superpowers/plans/2026-11-18-09-events-kafka.md)
- Старт: [docs/start.md](docs/start.md)
- Архитектура: [docs/architecture.md](docs/architecture.md)
- Выводы: [docs/learning-notes.md](docs/learning-notes.md)
