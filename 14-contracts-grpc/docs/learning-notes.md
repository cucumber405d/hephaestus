# 14 — выводы

## Таблица: изменение → кто поймал → на каком шаге

`scripts/breaking-change.sh all`: каждая строка — патч из `scripts/scenarios/`, наложенный на временную копию.
Сценарий засчитывается, только если упала **его** проверка (ошибка компиляции не в счёт). Если патч не наложился,
выход 2 («дрейф»), а не «поймано».

| # | Изменение | Кто поймал | Шаг | В монорепо с общим SNAPSHOT |
|---|---|---|---|---|
| 1 | `price_cents`: `int64` → `string` | `buf breaking` (WIRE) | сборка/CI `contracts` | компилятор — Ordering читает `long` |
| 2 | `lead_time_days` удалён, номер `reserved` (wire-совместимо) | Pact: `QuotePactProviderTest` | сборка `forge-service` | компилятор — Ordering читает поле |
| 3 | обязательное поле без default в `WorkOrderRequest` | Apicurio, `BACKWARD` | регистрация схемы (CI) | никто: собирается и падает в рантайме на старых сообщениях |
| 4 | символ enum `WORK_ORDER_FAILED` переименован | Apicurio, `BACKWARD` | регистрация схемы (CI) | компилятор — Ordering использует символ |
| 5 | AsyncAPI ссылается на несуществующую схему | AsyncAPI CLI | CI | никто |

Прогон 2026-11-24 (Windows, Git Bash, Docker Desktop): baseline зелёный по четырём проверкам, затем

```
CAUGHT by buf breaking (WIRE), contracts build
CAUGHT by Pact provider verification, forge-service build
CAUGHT by Apicurio BACKWARD rule, schema registration
CAUGHT by Apicurio BACKWARD rule, schema registration
CAUGHT by AsyncAPI validation, CI
```

Чем именно поймано (из `target/breaking-change/scenario-<n>.log`):

- 1 — buf: `Field "2" with name "price_cents" on message "QuoteReply" changed type from "int64" to "string"`.
- 2 — Pact: `$.lead_time_days Expected 0 to be equal to 3`. Forge перестал отправлять поле, proto3 прочитал 0.
- 3 и 4 — Apicurio: HTTP 400, `RuleViolationException` с именем поля или символа.

Последняя колонка — почему версии контракта зафиксированы в каждом сервисе. В одном репозитории и одной сборке
половину поломок ловит компилятор, и кажется, что контракты не нужны. Но в продакшене сервисы выкатываются по
отдельности, и между версиями живут сообщения в Kafka. Сценарий 3 компилятор не ловит вообще.

Почему для сценария 2 Pact, а не buf: удаление поля с `reserved` не ломает wire-формат, buf прав. Ломается
**ожидание** потребителя. Proto3 прочитает отсутствующее поле как 0, поэтому в pact-файле цена и срок —
точные значения, а не «любое число»: иначе 0 совпал бы по типу, и поломка прошла бы молча.

## Pact или Spring Cloud Contract

| | Pact | Spring Cloud Contract |
|---|---|---|
| Кто пишет контракт | потребитель, тестом своего клиента | обычно провайдер (DSL), потребитель берёт stubs |
| Языки | любые (Pact-спецификация, плагины) | JVM; другие — через Docker-обёртки |
| gRPC | protobuf-плагин (нативный бинарник) | нет нативной поддержки |
| Брокер | Pact Broker: версии, `can-i-deploy`, окружения | артефакт stubs в Maven-репозитории |
| В 14 | выбран: контракт — это то, что Ordering **читает** | — |

Без брокера `can-i-deploy` не ответить: pact-файл лежит в репозитории и проверяется на последней версии.
Следующий шаг — Pact Broker в платформе, публикация из CI, `can-i-deploy --to-environment` перед деплоем.

## REST или gRPC для котировки

- Контракт — код (`.proto`), клиент и сервер генерируются; поломку схемы ловит `buf`.
- Дедлайн — часть протокола: Ordering ставит 2 с, Forge видит, сколько осталось.
- Минусы: через Envoy Gateway нужен HTTP/2 и GRPCRoute, в браузере — только через gRPC-Web или Connect, curl
  заменяется на grpcurl.

## JSON или Avro

`MessageSizeTest`: тот же `ForgeEvent` — JSON 228 байт, Avro 153 байта плюс заголовок с id схемы (вывод теста). Avro не передаёт имена полей; без схемы сообщение не прочитать. Поэтому
реестр обязателен, а правило совместимости — главный контракт топика.

## Вопросы для собеседования

- Чем BACKWARD отличается от FORWARD и FULL, и кого обновлять первым при каждом?
- Почему удаление поля в proto3 — это `reserved`, а не просто удаление строки?
- Зачем Pact при наличии `buf breaking`? (сценарий 2)
- Почему сервисы не регистрируют схемы сами (`auto-register: false`)?
- Что изменится с Pact Broker и `can-i-deploy`?
