# 10-outbox-saga — outbox и сага в двух стилях

Заказ проходит **сагу** Ordering → Payment → Inventory → Delivery: резерв денег, резерв товара, доставка. Если шаг
отказал — **компенсации** откатывают предыдущие шаги в обратном порядке (release, refund). Сага живёт в двух
стилях **рядом**, стиль выбирается у заказа:

- **хореография** — сервисы реагируют на события друг друга, центра нет;
- **оркестрация** — Ordering командует шагами, ждёт ответы с дедлайном и компенсирует при таймауте.

Каждое сообщение уходит через **outbox** (Spring Modulith Events): оно сохраняется в той же транзакции, что и
изменение состояния, и отправляется в Kafka после коммита — dual write из 09 закрыт. История каждой саги видна
в `GET /api/orders/{id}`.

**Чему учит:** transactional outbox, сага и компенсации, идемпотентные участники, хореография против оркестрации,
таймауты шагов, «семантические блокировки» (отмена раньше действия).

- Спецификация: [docs/superpowers/specs/2026-11-18-10-outbox-saga-design.md](docs/superpowers/specs/2026-11-18-10-outbox-saga-design.md)
- План: [docs/superpowers/plans/2026-11-18-10-outbox-saga.md](docs/superpowers/plans/2026-11-18-10-outbox-saga.md)
- Старт: [docs/start.md](docs/start.md)
- Архитектура: [docs/architecture.md](docs/architecture.md)
- Выводы: [docs/learning-notes.md](docs/learning-notes.md)
