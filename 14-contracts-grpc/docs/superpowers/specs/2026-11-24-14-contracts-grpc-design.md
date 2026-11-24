# 14-contracts-grpc — design

## Зачем

В 13 контракт между монолитом и Forge-сервисом держится на договорённости: формат `forge.events` — это одинаковые
record-ы в двух кодовых базах, и ничто не мешает одной стороне его сломать. 14 — история о том, **как перестать
ломать соседей**: каждый вид контракта получает свой проверяемый формат, и для каждого вида поломки есть шаг
сборки, который её ловит раньше продакшена (решение Dmitry: одна сквозная история, продолжение 13, вариант 1;
контрактные тесты — Pact).

Продаётся на собеседовании как таблица «изменение → кто поймал → на каком шаге».

## Цели / не-цели / анти-цели

- **Цели**
  - Синхронный вызов **gRPC**: перед приёмом заказа на изделие на заказ Ordering спрашивает у Forge цену и срок
    (`QuoteService.Quote`). Контракт — `.proto`.
  - События `forge.requests` / `forge.events` — **Avro** со схемами в **Apicurio Registry 3**, правило совместимости
    `BACKWARD` (consumer, обновлённый первым, читает старые сообщения).
  - **AsyncAPI** 3 — описание обоих топиков со ссылками на те же `.avsc`; валидируется в CI.
  - **Pact** — потребитель (Ordering) фиксирует ожидания к gRPC-вызову (`pact-protobuf-plugin`); сборка Forge
    проверяет себя против pact-файла.
  - **Сценарии поломок** (`scripts/breaking-change.sh <сценарий>`): применяет изменение к копии контрактов и
    запускает проверку, которая обязана упасть; итог — таблица в learning-notes.
- **Не-цели**: Pact Broker и `can-i-deploy` (pact-файлы лежат в репозитории; брокер — в learning-notes), Strangler
  Fig и переключение (13), Spring Cloud Contract (только таблица сравнения в learning-notes), деплой на kind и
  Apicurio в платформе (14 — подпроект сборки и CI; запуск — Compose), gRPC через Gateway.
- **Анти-цели**: «зелёная» сборка, в которой контракт сломан, а проверка просто не запускается; общие классы
  событий в двух сервисах вместо сгенерированных из схемы.

## Состав

```
14-contracts-grpc/
├── contracts/        ← единственный источник: forge.proto, *.avsc, asyncapi.yaml, buf.yaml
├── ordering/         ← из modular/ 13 без migration/forge/legacy: потребитель gRPC и событий
├── forge-service/    ← из 13: поставщик gRPC, событий; Pact-верификация
├── supplier-stub/    ← копия из 13
├── scripts/          ← breaking-change.sh, e2e.sh
└── compose.yaml      ← Kafka, Apicurio Registry, две базы, сервисы
```

- `contracts/` — Maven-модуль: генерирует Java из `.proto` (protobuf-maven-plugin) и `.avsc` (avro-maven-plugin),
  оба сервиса зависят от него (внутри подпроекта это допустимо; правило «нет общих библиотек» — между
  подпроектами). Сгенерированные классы заменяют ручные record-ы событий из 13.
- Ordering принимает заказ так же, как в 13, но для изделия на заказ сначала вызывает `Quote` (дедлайн 2 с; при
  недоступности Forge — заказ `REJECTED` с причиной «quote unavailable»), сохраняет цену и срок в заказе.
- Сериализация Kafka — Apicurio Avro SerDe (`apicurio-registry-avro-serde-kafka`, v3); Modulith-outbox из 13
  остаётся, `@Externalized` публикует сгенерированный Avro-класс (проверить совместимость с Modulith JSON — за
  строителем, см. ниже).

## Сценарии поломок (минимальный набор)

| # | Изменение | Кто ловит | Где |
|---|---|---|---|
| 1 | Поле в `.proto` получило номер удалённого поля / сменило тип | `buf breaking` | сборка `contracts` |
| 2 | Из ответа `Quote` удалено поле, которое читает Ordering (wire-совместимо) | Pact (верификация провайдера) | сборка `forge-service` |
| 3 | В Avro-событие добавлено обязательное поле без default | Apicurio, правило `BACKWARD` | регистрация схемы |
| 4 | Переименован символ enum статуса в Avro | Apicurio + Pact сообщений? — уточняет строитель | |
| 5 | AsyncAPI ссылается на схему, которой нет / не совпадает | валидатор AsyncAPI | CI |

## Версии

Java 25, Boot 4.1.x, Spring gRPC 1.1.x (последняя стабильная — 1.1.1), Pact JVM 4.7.x, Apicurio Registry 3.x
(SerDe `apicurio-registry-avro-serde-kafka`), Avro 1.12.x, buf CLI. Перед планом строитель перепроверяет.

## Решения → ADR

- ADR-0012 — где живут контракты (модуль `contracts/`, генерация, single source of truth).
- ADR-0013 — Avro + Apicurio, правило совместимости и порядок выкатки (consumer first при BACKWARD).
- ADR-0014 — Pact (consumer-driven) вместо Spring Cloud Contract; почему без брокера на старте.

## Документация и CI

README, `docs/{start,architecture,learning-notes}.md` (таблица сценариев, Pact vs SCC, REST vs gRPC, JSON vs Avro —
размер сообщения). CI: `verify` (включая Pact-тесты и `buf breaking` против `main`), AsyncAPI-валидация,
`breaking-change.sh` для всех сценариев (каждый обязан упасть), Compose e2e (заказ на изделие на заказ проходит с
ценой из gRPC и Avro-событиями через Apicurio).

## За строителем

Точная схема `.proto` и `.avsc`, стратегия артефактов Apicurio (TopicIdStrategy и т. п.), как Modulith-outbox
сериализует Avro-класс, способ запуска `buf` (бинарник/Docker/maven), как применяются сценарии поломок (patch-файлы
и т. п.), разбиение на задачи.
