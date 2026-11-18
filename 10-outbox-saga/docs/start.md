# Start — 10-outbox-saga

Требования: JDK 25, Docker, curl. Скрипты — bash (Git Bash / WSL).

```bash
./mvnw -f 10-outbox-saga/pom.xml spotless:apply verify && git status   # тесты; закоммить правки spotless
10-outbox-saga/scripts/e2e.sh                                          # все сценарии в обоих стилях
KEEP_STACK=true 10-outbox-saga/scripts/e2e.sh
docker compose -p hephaestus-10 -f 10-outbox-saga/compose.yaml down -v
```

Руками: `cd 10-outbox-saga && docker compose -p hephaestus-10 up -d` (после `../mvnw -f pom.xml package
jib:dockerBuild -DskipTests`). Kafka UI: <http://localhost:8085>. Порты: Ordering 8080, Payment 8081,
Inventory 8082, Delivery 8083 (только localhost).

```bash
order() { curl -s -H 'Content-Type: application/json' -d "{\"customerId\":\"$1\",\"saga\":\"$2\",\"lines\":[{\"sku\":\"$3\",\"quantity\":1}]}" localhost:8080/api/orders; echo; }
show()  { curl -s localhost:8080/api/orders/$1; echo; }
```

Покупатели: alice (лимит 1000 EUR), bob (50 EUR), остальные 500 EUR. Склад: ANVIL-01 10, HAMMER-01 25, TONGS-01 0.
Цены: ANVIL-01 120.00, HAMMER-01 35.00, TONGS-01 18.50.

## Сценарии (стиль: `choreography` или `orchestration`)

1. `order alice orchestration HAMMER-01` → `show <id>`: история `→ ReservePayment`, `← PaymentReserved`, … `order CONFIRMED`.
2. `order bob choreography ANVIL-01` → REJECTED «credit limit exceeded».
3. `order alice orchestration TONGS-01` → REJECTED, в истории `→ RefundPayment` и `← PaymentRefunded`.
4. `curl -X PUT -H 'Content-Type: application/json' -d '{"failCustomers":["carol"],"delayMs":0}' localhost:8083/admin/faults`,
   `order carol choreography HAMMER-01` → REJECTED, затем `StockReleased` и `PaymentRefunded`.
5. **Outbox:** `docker compose -p hephaestus-10 stop kafka`, `order alice orchestration HAMMER-01` → 202;
   `docker exec -it hephaestus-10-postgres-1 psql -U postgres -d ordering -c "select event_type, completion_date from event_publication order by publication_date desc limit 3"`
   — публикация без `completion_date`; `docker compose -p hephaestus-10 start kafka` → через ~10–20 s заказ CONFIRMED.
6. **Дубль:** `curl -X POST localhost:8080/admin/republish/<id>` → `curl -s localhost:8081/admin/stats` (`duplicates` +1).
7. **Участник лежит:** `docker compose -p hephaestus-10 stop delivery`, по заказу в каждом стиле.
   Оркестрация: через 30 s (в e2e — 60 s) REJECTED «timeout at step delivery», команды компенсации в истории.
   Хореография: PENDING сколько угодно. `start delivery` → хореография доходит до CONFIRMED, а оркестрация получает
   поздний `DeliveryScheduled` (игнорируется) и `DeliveryCancelled`.
8. **Замедление:** `"delayMs":3000` у Payment и оркестрация с `SAGA_STEP_TIMEOUT=2s` в `compose.yaml` — таймаут
   на шаге оплаты, хотя Payment жив: таймаут не отличает «медленно» от «умер».
