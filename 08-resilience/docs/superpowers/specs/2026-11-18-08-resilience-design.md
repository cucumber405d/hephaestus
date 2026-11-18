# 08-resilience — design

## Зачем

Inventory зависит от внешнего, ненадёжного **Supplier** (поставщик материалов: цена, срок поставки, сколько может
отгрузить). Цель — **своими руками увидеть и сравнить** три способа пережить плохого соседа на одной и той же беде:

1. **Resilience4j** напрямую;
2. **Spring Cloud Circuit Breaker** (абстракция Spring Cloud, реализация — Resilience4j);
3. **встроенная устойчивость Spring Framework 7** (`@Retryable`, `@ConcurrencyLimit`; circuit breaker в ней нет).

Плюс **Redis** как запасной ответ (последнее известное значение) и **Toxiproxy**, чтобы ломать сеть в тестах и в e2e.

Успех: e2e включает задержку, обрыв и ошибки Supplier и одним прогоном показывает, как ведут себя три механизма
(сколько ждёт клиент, сколько запросов долетает до Supplier, что отдаётся пользователю); learning notes содержат
таблицу сравнения по коду, конфигурации, возможностям и метрикам.

## Цели / не-цели / анти-цели

- Цели: circuit breaker (closed → open → half-open), retry с backoff и jitter, timeout, bulkhead, rate limiter,
  fallback из кэша; метрики (Actuator/Micrometer); сравнение трёх механизмов на одинаковых сценариях.
- Не-цели: безопасность (без Keycloak — стенд лёгкий), Ordering и Gateway, развёртывание в кластер (resilience
  в mesh — тема 19; chaos в кластере — позже), распределённый rate limiting.
- Анти-цели: «магия», которую не видно — каждый механизм с явной конфигурацией и тестом, доказывающим поведение;
  ретраи неидемпотентных операций (Supplier только читаем).

## Подход (выбран вариант 1 из трёх)

| Вариант | Суть | Почему нет / да |
|---|---|---|
| **1. Три клиента рядом** ✔ | Три адаптера одного порта, три эндпоинта, один Supplier за Toxiproxy | Разница видна в одном прогоне; учебная витрина, не реальный сервис |
| 2. Один клиент, механизм по профилю | Выбор при запуске | Сравнение — несколько прогонов с перезапуском |
| 3. Только Spring Cloud CB | Сравнение на словах | Нет сравнения руками |

## Состав (Compose, без k8s)

```
client ──► Inventory ──► Toxiproxy :8666 ──► Supplier :8080
              │
              └──► Redis (кэш последних предложений)
              └──► PostgreSQL (склад, как в 06)
```

- **Supplier** (`08-resilience/supplier`): новый маленький Spring Boot сервис «внешнего поставщика».
  `GET /api/offers/{sku}` → `{sku, price, currency, leadTimeDays, maxQuantity}`; данные — статичная таблица в коде.
  Свои неисправности для ошибок уровня HTTP (Toxiproxy умеет только сеть): `PUT /admin/faults`
  `{"errorRate":0.0..1.0,"status":503,"delayMs":0}` и `DELETE /admin/faults`; счётчик запросов
  `GET /admin/stats` (сколько вызовов дошло) — e2e по нему доказывает «breaker не пускает».
- **Inventory** (копия Inventory из 06, без Ordering/Gateway): порт `SupplierOffers` и три адаптера;
  эндпоинты `GET /api/supply/{mechanism}/{sku}`, `mechanism ∈ {r4j, sccb, spring}`. Ответ:
  `{sku, price, leadTimeDays, maxQuantity, source: "live"|"cache", fetchedAt, mechanism}`; нет ни живого ответа,
  ни кэша → `503` Problem Details `supplier-unavailable` + `Retry-After`.
- **Toxiproxy** (`ghcr.io/shopify/toxiproxy`), прокси `supplier` создаётся при старте (конфиг-файл).
- **Redis**: кэш `offer:{sku}`, TTL 10 мин, пишется при каждом живом ответе любого механизма, читается в fallback.

## Механизмы и одинаковые параметры

Все три настроены на одни числа, чтобы сравнение было честным:
read timeout 1 s (у HTTP-клиента), 3 попытки, backoff 200 ms ×2 с jitter, bulkhead 10 параллельных вызовов.
Circuit breaker (где есть): окно 10 вызовов, порог отказов 50 %, медленный вызов > 800 ms считается отказом,
open 10 s, half-open 3 пробных. Rate limiter (только Resilience4j): 20 запросов/с к Supplier.

| | Resilience4j | Spring Cloud CB | Spring 7 |
|---|---|---|---|
| Как подключается | стартер для Boot 3, если работает на Boot 4; иначе ядро и программные декораторы (решение при сборке, фиксируется) | `spring-cloud-starter-circuitbreaker-resilience4j`, `CircuitBreakerFactory.create("supplier").run(call, fallback)` + `Customizer` | `@EnableResilientMethods`, `@Retryable`, `@ConcurrencyLimit` |
| CB / retry / bulkhead / rate limit | все | CB + time limiter (+ bulkhead через R4J) ; retry отдельно | retry + concurrency limit; **без CB** |
| Fallback | метод/лямбда | второй аргумент `run` | try/catch вокруг вызова |

Ожидаемое различие, которое e2e должен показать: при «Supplier лежит» Spring 7 продолжает долбить его ретраями
(счётчик в `/admin/stats` растёт ~×3 на запрос), а оба CB после порога отвечают из кэша мгновенно и Supplier
вызовов не получает, пока не пройдёт open-интервал.

## Тесты

- Unit/slice как в 06; **IT на Testcontainers**: Redis + Toxiproxy (модуль Testcontainers) + Supplier-заглушка на
  JDK HttpServer за Toxiproxy. На каждый механизм (параметризованные тесты): задержка > timeout → fallback;
  отказ → breaker открывается после порога и быстрые ответы без вызовов Supplier (Spring 7 — ретраи видны);
  восстановление → half-open → closed; пустой кэш → 503.
- **e2e.sh** (Compose): Toxiproxy `latency`, `timeout`/`reset_peer`, Supplier `/admin/faults` 503; по каждому
  механизму — время ответа, `source`, прирост `/admin/stats`; итоговая таблица в выводе.

## Ограничения

Java 25, Boot 4.1.2, Spring Cloud 2025.1.3, Testcontainers 2; Jib-образы `hephaestus/resilience-{inventory,supplier}`;
CI как в 06/07 (verify → e2e → publish); без k8s и без флага на платформе.
Пакеты `de.ibikus.appa.hephaestus.resilience.{inventory,supplier}`.

## Оставлено исполнителю

Точные имена классов и свойств; как именно оформить три адаптера (общий базовый класс или нет); формат итоговой
таблицы e2e; метрики — достаточно Actuator `/actuator/circuitbreakers`, `/actuator/metrics`.

## Мои решения (можно оспорить)

- Без k8s и платформы: тема — устойчивость в коде; в кластере её сравним с mesh в 19.
- Supplier — собственный сервис, а не WireMock: виден как «чужая система», есть счётчик вызовов и управляемые 5xx.
- Rate limiter только у Resilience4j: у Spring Cloud CB и Spring 7 его нет — это и есть пункт сравнения.
