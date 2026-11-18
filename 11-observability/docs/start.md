# 11-observability — старт

## Сборка и стек

```bash
./mvnw -f 11-observability/pom.xml spotless:apply verify          # тесты (Docker для Testcontainers)
11-observability/scripts/e2e.sh                                    # всё автоматом, ~10 минут
```

Руками:

```bash
cd 11-observability
../mvnw -f pom.xml package jib:dockerBuild -DskipTests
docker compose -p hephaestus-11 up -d
```

| Что | Где |
|---|---|
| Grafana (admin/admin) | http://localhost:3000 — дашборд **Hephaestus → Saga**, Explore → Tempo / Loki / Prometheus |
| Kafka UI (заголовки `traceparent` у сообщений) | http://localhost:8085 |
| Ordering API | http://localhost:8080/api/orders |
| Рычаги участников | http://localhost:8081-8083/admin/faults |

Заказ (как в 10):

```bash
curl -s -H 'Content-Type: application/json' localhost:8080/api/orders \
  -d '{"customerId":"alice","saga":"orchestration","lines":[{"sku":"HAMMER-01","quantity":1}]}'
```

Найти его трейс: Grafana → Explore → Tempo → TraceQL `{ span.order.id = "<id>" }`. Из спана — кнопка «Logs for
this span» (Loki), из лога — ссылка на трейс.

## Сценарии

1. **Успешный заказ** (оба стиля). Один трейс; спаны `ordering`, `payment`, `inventory` и `delivery`. Сравни
   спаны Delivery (агент: имена и атрибуты по OTel semantic conventions, JDBC-запросы видны) и остальных
   (Micrometer: `order.id`, `saga.style`, `message.type`, без JDBC).
2. **Отказ участника** (bob, `ANVIL-01` — лимит 50 EUR; или `TONGS-01` — нет на складе). В трейсе отказ и
   компенсации; на дашборде — `rejected` с причиной.
3. **Kafka остановлена** → заказ → через пару минут Kafka запущена: `docker compose -p hephaestus-11 stop kafka`,
   заказ, `start kafka`. Трейс один, в нём пауза между HTTP-запросом и `outbox relay`. Если Kafka лежала дольше
   минуты — в Grafana (Alerting → Alert rules) правило **Outbox backlog** переходит в Firing и гаснет после
   запуска Kafka. Панель «Outbox backlog» показывает очередь.
   - **3a. Без переноса контекста:** `OUTBOX_CARRY_TRACE=false docker compose -p hephaestus-11 up -d` и снова
     сценарий 3 — переотправленные сообщения начинают **новые** трейсы, сага разваливается на куски.
   - **3b. Минус агента:** останови Kafka, когда заказ уже дошёл до Delivery (`delayMs` у Delivery, например
     `curl -X PUT -H 'Content-Type: application/json' -d '{"delayMs":20000}' localhost:8083/admin/faults`).
     Ответ Delivery переотправляется планировщиком Modulith и уходит в отдельный трейс: агент не знает, что
     outbox — это отложенная отправка.
4. **Delivery остановлена**, оркестрация: через 30 s спан `saga timeout` внутри трейса саги, за ним компенсации;
   `saga_timeouts_total` растёт.
5. **Медленный участник:** `{"delayMs":3000}` у Payment — длинный спан в трейсе, рост p95 на панели шагов.

Логи в Loki: `{service_name="ordering"} | trace_id="<trace id>"`. В консоли (`docker compose logs ordering`)
— те же записи в ECS JSON с `trace.id`.
