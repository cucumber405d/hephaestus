# 07-security — вход, токены, роли

Магазин из 06 за **Keycloak**. **Gateway** — Backend for Frontend: вход через Authorization Code + PKCE,
токены живут в серверной сессии, браузер держит только HttpOnly-cookie; на маршрутах `/api/**` Gateway
подставляет access token пользователя (`TokenRelay`) и защищает POST от CSRF (cookie `XSRF-TOKEN` + заголовок
`X-XSRF-TOKEN`). **Ordering** и **Inventory** — OAuth2 Resource Server: проверяют подпись, `iss`, `aud`, роли
realm. Заказ принадлежит пользователю (`sub`), чужой — 404. Ordering резервирует товар в Inventory **своим**
токеном (client credentials) — токеном пользователя резервировать нельзя. В кластере — NetworkPolicy default deny.

**Чему учит:** OAuth2/OIDC без магии (какой запрос идёт куда), PKCE, BFF против токенов в браузере, CSRF при
cookie-сессии, валидация JWT (issuer, audience, ключи), публичный и внутренний адрес Keycloak, роли и владение
данными, сервисная идентичность, сетевые политики.

- Спецификация: [docs/superpowers/specs/2026-11-17-07-security-design.md](docs/superpowers/specs/2026-11-17-07-security-design.md)
- План: [docs/superpowers/plans/2026-11-17-07-security.md](docs/superpowers/plans/2026-11-17-07-security.md)
- Старт: [docs/start.md](docs/start.md)
- Архитектура: [docs/architecture.md](docs/architecture.md)
- Выводы: [docs/learning-notes.md](docs/learning-notes.md)
