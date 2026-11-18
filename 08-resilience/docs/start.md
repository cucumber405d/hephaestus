# Start — 08-resilience

Требования: JDK 25, Docker, curl. Скрипты — bash (Git Bash / WSL).

```bash
# Сборка и все тесты (Testcontainers: PostgreSQL, Redis, Toxiproxy)
./mvnw -f 08-resilience/pom.xml spotless:apply verify

# Стек в Compose + сравнение механизмов (печатает таблицу)
08-resilience/scripts/e2e.sh
KEEP_STACK=true 08-resilience/scripts/e2e.sh
docker compose -p hephaestus-08 -f 08-resilience/compose.yaml down -v
```

## Руками (стек поднят)

```bash
curl -s localhost:8080/api/supply/r4j/ANVIL-01        # source: live (и в кэш)
curl -s localhost:8080/api/supply/sccb/ANVIL-01
curl -s localhost:8080/api/supply/spring/ANVIL-01

# Сеть: задержка 1500 ms (таймаут чтения 1 s) -> source: cache
curl -s -d '{"name":"latency","type":"latency","attributes":{"latency":1500}}' localhost:8474/proxies/supplier/toxics
curl -s -X DELETE localhost:8474/proxies/supplier/toxics/latency

# Поставщик: 503 на всё, затем счётчик вызовов, которые до него дошли
curl -s -X PUT -H 'Content-Type: application/json' -d '{"errorRate":1.0,"status":503,"delayMs":0}' localhost:8090/admin/faults
for i in $(seq 1 12); do curl -s -o /dev/null localhost:8080/api/supply/spring/ANVIL-01; done
curl -s localhost:8090/admin/stats                    # +36: у Spring 7 нет circuit breaker
curl -s -X DELETE localhost:8090/admin/stats
for i in $(seq 1 12); do curl -s -o /dev/null localhost:8080/api/supply/r4j/ANVIL-01; done
curl -s localhost:8090/admin/stats                    # заметно меньше: breaker открылся
curl -s -X DELETE localhost:8090/admin/faults

# Состояние и метрики
curl -s localhost:8080/actuator/metrics/resilience4j.circuitbreaker.state
```

Порты: Inventory `8080`, Supplier `8090` (API и `/admin`), Toxiproxy API `8474`.
