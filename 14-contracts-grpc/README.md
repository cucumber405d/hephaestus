# 14-contracts-grpc — как перестать ломать соседей

Продолжение 13: Ordering (монолит после переключения) и Forge-сервис. В 13 контракт между ними — договорённость:
одинаковые record-ы в двух кодовых базах, и ничто не мешает одной стороне его сломать. Здесь у каждого вида
контракта есть проверяемый формат, а у каждого вида поломки — шаг сборки, который ловит её раньше продакшена.

| Контракт | Формат | Кто проверяет |
|---|---|---|
| Котировка (синхронно) | gRPC, [`quote.proto`](contracts/src/main/proto/hephaestus/forge/v1/quote.proto) | `buf breaking` (WIRE) + Pact (protobuf-плагин) |
| `forge.requests`, `forge.events` | Avro, [`*.avsc`](contracts/src/main/avro) в Apicurio Registry | правило `BACKWARD` в реестре |
| Описание топиков | [AsyncAPI 3](contracts/asyncapi.yaml), ссылки на те же `.avsc` | AsyncAPI CLI |

Модули: `contracts/` (единственный источник контрактов, своя версия 1.1.0, рядом замороженная
[`released/1.0.0`](contracts/released/README.md)), `ordering/`, `forge-service/`, `supplier-stub/`.

Новое в домене: перед приёмом заказа на изделие на заказ Ordering спрашивает у Forge **обязывающую** котировку —
цену и срок. Forge принимает наряд, только если котировка существует, не истекла и цена та же.

**Главное для собеседования:** [`scripts/breaking-change.sh`](scripts/breaking-change.sh) — пять поломок, каждая
падает на своём шаге; таблица — в [learning-notes](docs/learning-notes.md).

**Решения:** [ADR-0012](../docs/adr/0012-contracts-module.md) (модуль контрактов),
[ADR-0013](../docs/adr/0013-avro-apicurio-backward.md) (Avro, Apicurio, BACKWARD),
[ADR-0014](../docs/adr/0014-pact-consumer-driven.md) (Pact вместо Spring Cloud Contract).

**Документы:** [start](docs/start.md) — команды; [architecture](docs/architecture.md) — потоки и порядок
выкатки; [learning-notes](docs/learning-notes.md) — выводы и вопросы для собеседования.
