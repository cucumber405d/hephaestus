# 06-service-communication — сервис ↔ сервис

**Ordering** размещает заказ и синхронно резервирует товар в **Inventory** через декларативный HTTP-клиент
Spring Framework 7 (`@HttpExchange` над `RestClient`) с явными таймаутами и переводом чужих ошибок в свои
(`409`/`422`/`503 + Retry-After`). **Inventory** версионирует API заголовком `API-Version` (v1 устарел —
`Deprecation`/`Sunset`). **Spring Cloud Gateway** (Server Web MVC) — публичный вход: выставляет только
публичные пути, внутренний `/api/reservations` снаружи недоступен. В Kubernetes сервисы находят друг друга по
DNS имён Service; деплой — на платформу 05 через Argo CD.

**Чему учит:** HTTP Service Client, таймауты и бюджет времени, перевод ошибок соседа, почему пока без ретраев,
удалённый вызов вне транзакции БД, блокировки при резерве, версионирование API и его устаревание, API Gateway
против Ingress/Gateway API, `X-Forwarded-*`, e2e через вход.

- Спецификация: [docs/superpowers/specs/2026-11-17-06-service-communication-design.md](docs/superpowers/specs/2026-11-17-06-service-communication-design.md)
- План: [docs/superpowers/plans/2026-11-17-06-service-communication.md](docs/superpowers/plans/2026-11-17-06-service-communication.md)
- Старт: [docs/start.md](docs/start.md)
- Архитектура: [docs/architecture.md](docs/architecture.md)
- Выводы: [docs/learning-notes.md](docs/learning-notes.md)
