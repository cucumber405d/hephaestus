# 09-events-kafka — design

## Зачем

Первое асинхронное взаимодействие: **Ordering** публикует событие «заказ размещён» в Kafka, новый сервис
**Notification** уведомляет покупателя. Цель — **потрогать Kafka руками**: стенд с рычагами (сбои по команде,
масштабирование, Kafka UI), где каждый сценарий виден глазами и проверяется e2e.

Успех: по `docs/start.md` можно руками повторить каждый сценарий ниже и увидеть результат в Kafka UI и в
ответах сервисов; e2e проходит все сценарии одним прогоном.

## Цели / не-цели / анти-цели

- Цели: топик с партициями и ключом; consumer group и ребалансировка при масштабировании; ретраи с backoff →
  **DLT** → переотправка из DLT; **идемпотентный consumer** (дубли не дают второго уведомления); порядок событий
  одного покупателя; **share group** (очереди Kafka 4.2) рядом с классической группой.
- Не-цели: outbox и саги (тема 10 — здесь dual write «БД, затем Kafka» честно остаётся и описан), безопасность
  (Keycloak), Inventory (заглушка внутри Ordering), Schema Registry/Avro (JSON), k8s/Strimzi (позже).
- Анти-цели: магия без проверки; «exactly once» на словах — здесь at-least-once + идемпотентность, и это сказано.

## Подход (выбран вариант 1)

| Вариант | Суть | Решение |
|---|---|---|
| **1. Площадка в Compose с рычагами** | Ordering + Notification + Kafka + Kafka UI, сбои по команде, `--scale` | ✔ |
| 2. Без share groups | Проще | очереди только на словах — нет |
| 3. Сразу в кластер (Strimzi) | Ближе к реальности | опыты медленнее, нужен профиль full — позже |

## Состав (Compose)

```
client → Ordering ──(orders.events, key = customerId)──► Kafka 4.2 (1 брокер, KRaft)
                                                          ├─► Notification ×N  (consumer group "notification")
                                                          └─► Notification-share ×N (share group "notification-share")
          Kafka UI :8085 — топики, партиции, офсеты, лаг, сообщения
```

- **Kafka**: `apache/kafka:4.2.2`, один брокер KRaft, share groups включены, `share.coordinator.state.topic`
  с репликацией 1. Топики создаются приложением (`NewTopic`): `orders.events` — **3 партиции**, DLT —
  1 партиция.
- **Ordering** (`09-events-kafka/ordering`, из 06 без Keycloak): `POST /api/orders {customerId, lines}`;
  InventoryPort — локальная заглушка «всегда зарезервировано». После сохранения заказа публикует
  `OrderPlaced {eventId (UUID), orderId, customerId, lines, occurredAt}` в `orders.events` с ключом
  `customerId` и заголовком типа события, отправка синхронная (ждёт подтверждения брокера, `acks=all`,
  идемпотентный producer). Ошибка отправки → 503; заказ при этом уже сохранён — **dual write**, явно
  описан в learning notes как мостик к outbox (10).
  Дубль для опытов: `POST /admin/republish/{orderId}` — отправляет то же событие (тот же `eventId`) ещё раз.
- **Notification** (`09-events-kafka/notification`, новый): `@KafkaListener` на `orders.events`, своя PostgreSQL.
  Обработка в одной транзакции БД: вставка `processed_event(event_id PK)` + `notification(id, customer_id,
  order_id, channel=EMAIL, text, created_at)`; дубль `event_id` → пропуск (лог + счётчик). «Отправка» — запись
  и лог. `GET /api/notifications?customerId=` — что получил покупатель.
  - Ошибки: `DefaultErrorHandler` + `ExponentialBackOff` (1 s, ×2, 3 повтора) → `DeadLetterPublishingRecoverer`
    в DLT; невосстановимые (битый JSON, неизвестный тип, валидация) — сразу в DLT без повторов.
  - Рычаги `PUT /admin/faults {failCustomers: [..], transientFailures: n, delayMs}`: всегда падать для
    покупателей (→ DLT), падать первые n попыток (→ ретраи и успех), тормозить (→ лаг).
  - `POST /admin/dlt/replay` — перечитать DLT и отправить записи обратно в `orders.events` (после починки).
  - `GET /admin/stats` — по экземпляру: hostname, обработано, дублей пропущено, назначенные партиции.
  - Режим `notification.mode=share` (сервис `notification-share` в Compose): тот же обработчик через
    `ShareKafkaListenerContainerFactory`, группа `notification-share`; подтверждение по записи; DLT у share
    groups нет — неудачная запись после лимита доставок архивируется (это пункт сравнения).
- **Kafka UI**: `ghcr.io/kafbat/kafka-ui`, порт 8085.
- Порты: Ordering 8080; Notification `8091-8095` (для `--scale`); Notification-share `8096-8099`; Kafka UI 8085.

## Сценарии (start.md + e2e)

1. **Счастливый путь**: заказ → уведомление; в Kafka UI событие в партиции по ключу.
2. **Порядок**: 5 заказов одного покупателя — одна партиция, уведомления в порядке заказов.
3. **Временный сбой**: `transientFailures: 2` → два повтора с паузами 1 s, 2 s → успех, в DLT пусто.
4. **Постоянный сбой → DLT**: `failCustomers: [bob]` → после 3 повторов запись в DLT, уведомления нет;
   снять сбой → `dlt/replay` → уведомление появилось.
5. **Дубль**: `admin/republish` → второе событие обработано, уведомление одно, счётчик дублей +1.
6. **Масштабирование**: `--scale notification=3` → у каждого по партиции (stats); `=5` → двое без партиций;
   остановить один → ребалансировка.
7. **Share group**: `notification-share` ×5 при тех же 3 партициях → работу получают больше трёх экземпляров;
   порядок одного покупателя не гарантирован (видно по времени).
8. **Лаг**: `delayMs: 2000` + пачка заказов → лаг растёт в Kafka UI, затем рассасывается.

## Тесты

Unit/slice как в 06; IT на Testcontainers (`org.testcontainers.kafka.KafkaContainer`, `apache/kafka:4.2.2`;
PostgreSQL): Ordering публикует с ключом и заголовком; Notification — успех, временный сбой → повторы,
постоянный → DLT, битый JSON → DLT без повторов, дубль → одно уведомление, replay из DLT; share-режим
обрабатывает и подтверждает. e2e (Compose) — сценарии 1–7 (8 — только руками).

## Ограничения

Java 25, Boot 4.1.2 (Spring Kafka 4.x, Jackson 3), Testcontainers 2; Jib-образы
`hephaestus/events-kafka-{ordering,notification}`; CI verify → e2e → publish; без k8s.
Пакеты `de.ibikus.appa.hephaestus.eventskafka.{ordering,notification}`.

## Мои решения (можно оспорить)

- `customerId` в теле запроса (без Keycloak) — ключ партиции и владелец уведомлений.
- JSON без Schema Registry; тип события в заголовке, класс-получатель свой у Notification (без общей библиотеки
  моделей — контракт описан в docs).
- Идемпотентность через таблицу обработанных событий в той же транзакции, а не через Kafka transactions.
- Если share groups в Spring Kafka/брокере не заведутся — сценарий 7 откладывается, фиксируется в итоге.
