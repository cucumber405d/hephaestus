# Start — 09-events-kafka

Требования: JDK 25, Docker, curl. Скрипты — bash (Git Bash / WSL).

```bash
./mvnw -f 09-events-kafka/pom.xml spotless:apply verify          # тесты (Testcontainers: Kafka 4.2, PostgreSQL)
09-events-kafka/scripts/e2e.sh                                    # сценарии 1–7 в Compose
KEEP_STACK=true 09-events-kafka/scripts/e2e.sh
docker compose -p hephaestus-09 -f 09-events-kafka/compose.yaml --profile share down -v
```

Поднять руками: `cd 09-events-kafka && docker compose -p hephaestus-09 up -d` (после сборки образов:
`../mvnw -f pom.xml package jib:dockerBuild -DskipTests`). **Kafka UI: <http://localhost:8085>** — Topics →
`orders.events` → Messages (ключ, партиция, заголовок `eventType`); Consumers → `notification` (лаг, партиции).

Порты (только `127.0.0.1`: эндпоинты `/admin` открыты без входа — это площадка, не сервис): Ordering `8080`,
Notification `8091…8095`, Notification-share `8096…8100`, Kafka с хоста `localhost:9094`.

```bash
order() { curl -s -H 'Content-Type: application/json' -d "{\"customerId\":\"$1\",\"lines\":[{\"sku\":\"ANVIL-01\",\"quantity\":1}]}" localhost:8080/api/orders; echo; }
seen()  { curl -s "localhost:8091/api/notifications?customerId=$1"; echo; }
```

## Сценарии

1. **Счастливый путь:** `order alice; seen alice` — в Kafka UI событие в одной из партиций, ключ `alice`.
2. **Порядок:** `for i in 1 2 3 4 5; do order erin; done; seen erin` — все в одной партиции, уведомления по порядку.
3. **Временный сбой:**
   `curl -X PUT -H 'Content-Type: application/json' -d '{"failCustomers":[],"transientFailures":2,"delayMs":0}' localhost:8091/admin/faults`,
   `order frank` — в логах Notification две ошибки с паузами 1 s и 2 s, затем успех.
4. **DLT и переотправка:** сбой для `bob` (`"failCustomers":["bob"]`), `order bob; order carol` — carol уведомлена
   сразу, bob через ~7 s в топике `orders.events-dlt` (в Kafka UI — заголовки `kafka_dlt-exception-*`).
   Починить: `curl -X DELETE localhost:8091/admin/faults`, затем `curl -X POST localhost:8091/admin/dlt/replay` → `seen bob`.
   Replay читает DLT до конца на момент вызова и пропускает невалидные события (`skipped`).
4а. **Poison message:** в Kafka UI → `orders.events` → Produce Message с заголовком `eventType=OrderPlaced` и
   телом `not json` — сразу в DLT, без ретраев.
5. **Дубль:** `id=$(order alice | sed 's/^{"id":"\([^"]*\)".*/\1/'); curl -X POST localhost:8080/admin/republish/$id`
   → `curl -s localhost:8091/admin/stats` (`duplicates` +1), `seen alice` — уведомление одно.
6. **Масштабирование:** `docker compose -p hephaestus-09 up -d --scale notification=3 notification`, затем
   `for p in 8091 8092 8093; do curl -s localhost:$p/admin/stats; echo; done` — по партиции на экземпляр;
   `--scale notification=5` — двое без партиций; `docker stop <контейнер>` — партиции переезжают (Kafka UI → Consumers).
7. **Share group:** `docker compose -p hephaestus-09 --profile share up -d --scale notification-share=5`,
   задержка 300 ms на каждом (`/admin/faults` на 8096…8100), 30 заказов разным покупателям — `processed` в
   `/admin/stats` растёт у всех пяти, хотя партиций три.
8. **Лаг:** `"delayMs":2000` на 8091 и 20 заказов — в Kafka UI лаг группы `notification` растёт, затем падает.
