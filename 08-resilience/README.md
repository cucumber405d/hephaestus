# 08-resilience — устойчивость к ненадёжному соседу

**Inventory** спрашивает у внешнего **Supplier** (поставщика) цену, срок поставки и объём. Supplier ненадёжен:
тормозит, падает, рвёт соединения. Один и тот же вызов защищён **тремя механизмами рядом** —
`/api/supply/{r4j|sccb|spring}/{sku}`:

1. **Resilience4j** напрямую — rate limiter, bulkhead, retry, circuit breaker;
2. **Spring Cloud Circuit Breaker** — абстракция Spring Cloud, под ней Resilience4j (circuit breaker + time limiter);
3. **встроенная устойчивость Spring Framework 7** — `@Retryable` и `@ConcurrencyLimit`, без circuit breaker.

Когда поставщик не отвечает, Inventory отдаёт **последнее известное предложение из Redis** (`source: "cache"`).
Сбои устраивает **Toxiproxy** (сеть) и сам Supplier (`/admin/faults`: 5xx, задержки); e2e печатает таблицу сравнения.

**Чему учит:** таймауты как первая защита, retry с backoff и jitter (и когда он вредит), circuit breaker и его
состояния, bulkhead и rate limiter, fallback и устаревшие данные, «шторм ретраев», сравнение библиотеки,
абстракции и встроенного механизма, chaos-тесты с Toxiproxy.

- Спецификация: [docs/superpowers/specs/2026-11-18-08-resilience-design.md](docs/superpowers/specs/2026-11-18-08-resilience-design.md)
- План: [docs/superpowers/plans/2026-11-18-08-resilience.md](docs/superpowers/plans/2026-11-18-08-resilience.md)
- Старт: [docs/start.md](docs/start.md)
- Архитектура: [docs/architecture.md](docs/architecture.md)
- Выводы: [docs/learning-notes.md](docs/learning-notes.md)
