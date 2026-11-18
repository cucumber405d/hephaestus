# 07-security — спецификация

- **Статус:** утверждена (режим «всё сразу»; решения исполнителя — §2)
- **Дата:** 2026-11-17
- **Корневая спека:** [../../../../docs/superpowers/specs/2026-11-05-hephaestus-root-design.md](../../../../docs/superpowers/specs/2026-11-05-hephaestus-root-design.md)
- **Основа:** копия 06 (Inventory, Ordering, Gateway) + безопасность; Keycloak из платформы 05

## 1. Цель

Защитить магазин из 06 стандартными средствами: **Keycloak** (realm `hephaestus`, роли `customer`/`smith`/`admin`),
**Gateway как BFF** (OAuth2 Authorization Code + PKCE, токены только на сервере, браузеру — сессионная cookie,
CSRF), **TokenRelay** (Gateway передаёт токен пользователя сервисам), Ordering и Inventory — **OAuth2 Resource
Server** (подпись, issuer, audience, роли), **Client Credentials** для вызова Ordering → Inventory от имени сервиса,
владение заказом, **NetworkPolicy** в кластере.

**Критерии успеха**
- `verify` зелёный: правила доступа на web slice с `jwt()`, клиент Client Credentials против заглушки токен-эндпоинта.
- `scripts/e2e.sh` (Compose + Keycloak): вход alice через форму Keycloak → сессия Gateway; заказ 201; без CSRF 403;
  без входа 401; bob (smith) не может заказать 403; чужой заказ 404; прямые вызовы сервисов: без токена 401, токен
  пользователя на внутренний резерв 403.
- CI: verify → e2e → три образа; манифесты `k8s/` (realm, NetworkPolicy) и Application на платформе за флагом.

**Не цели:** mTLS (19), секреты вне Git (21), Token Exchange (заметки), фронтенд SPA, MFA.
**Анти-цели:** токены в браузере; сервисы, доверяющие любому токену realm без проверки audience; роли из заголовков.

## 2. Решения

| Тема | Решение | Отклонено |
|---|---|---|
| Вход пользователя | Gateway — BFF: `oauth2Login` с PKCE (S256), сессия, CSRF-cookie `XSRF-TOKEN` + заголовок `X-XSRF-TOKEN`; `/api/**` без сессии → 401 (не редирект) | Токены в SPA (public client); Gateway как Resource Server |
| Сервисы | Resource Server: `issuer-uri` (только сверка `iss`) + `jwk-set-uri` (внутренний адрес ключей) + `audiences`; роли из `realm_access.roles` → `ROLE_*` | Discovery по issuer (публичный адрес недоступен изнутри); проверка через introspection |
| Ordering → Inventory | Client Credentials (`ordering`, роль `inventory-reserver`) через `OAuth2ClientHttpRequestInterceptor` на `RestClient` | Проброс токена пользователя (у пользователя не должно быть права резервировать); Token Exchange (заметки) |
| Права | Inventory: склад — любой аутентифицированный, резерв — `inventory-reserver`; Ordering: заказ — `customer`, чтение — владелец (`sub`) или `admin`, иначе 404 | Проверки в Gateway |
| Адреса Keycloak | `hostname` публичный (`iss` всегда публичный), `backchannelDynamic: true` — сервисы ходят за ключами и токенами по внутреннему адресу | Один адрес везде (не работает в Compose и K8s) |
| Realm | JSON для Compose + `KeycloakRealmImport` в `k8s/` (одно содержание, два формата); клиенты `shop-gateway` (BFF), `ordering` (сервис), `inventory` (audience), `shop-cli` (только тесты, password grant) | Создание через Admin API скриптом |
| NetworkPolicy | default deny ingress в `hephaestus-07` + разрешения: Envoy → gateway → ordering → inventory; узлы kind (probes) | Без политик; политики для БД (CNPG — в заметки) |
| Тесты | `spring-security-test` (`jwt()`), заглушка токен-эндпоинта на JDK `HttpServer`; e2e — скриптованный вход через форму Keycloak | Testcontainers-Keycloak (зависимость, конвергенция) |

## 3. Доступ

| Запрос | alice (customer) | bob (smith) | admin | без входа |
|---|---|---|---|---|
| `GET /api/stock/{sku}` (через Gateway) | 200 | 200 | 200 | 401 |
| `POST /api/orders` (+CSRF) | 201 | 403 | 403 | 401 |
| `GET /api/orders/{id}` своего | 200 | — | 200 (любой) | 401 |
| `GET /api/orders/{id}` чужого | 404 | 404 | 200 | 401 |
| `POST /api/reservations` (напрямую) | 403 | 403 | 403 | 401; 201 только сервису `ordering` |

Хосты: Compose — Gateway `http://localhost:8080`, Keycloak `http://localhost:8180`; кластер — `http://secure-shop.localtest.me`,
Keycloak `http://keycloak.localtest.me`.

## 4. Learning-notes

OAuth2 и OIDC (роли, потоки, токены); почему PKCE и для confidential клиента; BFF против токенов в браузере; CSRF
и SameSite; JWT: подпись, `iss`, `aud`, `exp`; issuer против адреса ключей; роли Keycloak → authorities; Client
Credentials и идентичность сервиса; проброс токена против Token Exchange; авторизация по владению и 404 вместо 403;
NetworkPolicy (default deny, probes, CNI); вопросы для собеседования.
