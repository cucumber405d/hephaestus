# 06-service-communication — спецификация

- **Статус:** утверждена (режим «всё сразу», как 04–05; решения исполнителя — §2)
- **Дата:** 2026-11-17
- **Корневая спека:** [../../../../docs/superpowers/specs/2026-11-05-hephaestus-root-design.md](../../../../docs/superpowers/specs/2026-11-05-hephaestus-root-design.md)
- **Домен:** Ordering → Inventory ([docs/domain.md](../../../../docs/domain.md))

## 1. Цель

Синхронное общение сервисов: **Ordering** при размещении заказа резервирует товар в **Inventory** через
декларативный HTTP-клиент (`@HttpExchange` + `RestClient`), с таймаутами и переводом ошибок соседа в свои;
**Inventory** версионирует API (Spring Framework 7: заголовок `API-Version`, v1 устарел — `Deprecation`/`Sunset`);
**Spring Cloud Gateway (Server Web MVC)** — публичный вход приложения: маршрутизирует внешние пути и **не
выставляет** внутренние (`/api/reservations`). В кластере — K8s DNS (`http://inventory`), деплой на платформу 05.

**Критерии успеха**
- `./mvnw -f 06-service-communication/pom.xml verify` зелёный (unit, slices, Testcontainers, заглушки соседей на JDK `HttpServer`).
- `scripts/e2e.sh`: весь стек в Compose из Jib-образов; через Gateway — v1/v2 склада, заказ 201, нет товара 409,
  неизвестный SKU 422, внутренний путь 404, Inventory остановлен → 503 + `Retry-After`.
- CI: verify → e2e → публикация трёх образов в GHCR (только `main`).
- Манифесты `k8s/` и Application на платформе (флаг `serviceCommunication`, включается после первой публикации).

**Не цели:** retries, circuit breaker, кэш (08); асинхронность и саги, компенсация резерва (09–10);
безопасность (07); трассировка (11); контрактные тесты (14).
**Анти-цели:** ретраи без идемпотентности; таймауты по умолчанию (бесконечность); внутренний API наружу.

## 2. Решения

| Тема | Решение | Отклонено |
|---|---|---|
| Модули | Reactor `service-communication`: `inventory`, `ordering`, `gateway` | Три подпроекта; общий модуль DTO (связность) |
| Клиент | Интерфейс `InventoryHttpApi` (`@HttpExchange`) + `HttpServiceProxyFactory` над `RestClient` из Boot-билдера; явные connect 1 s / read 2 s (`JdkClientHttpRequestFactory`) | OpenFeign (feature-complete); `@ImportHttpServices` (меньше контроля над таймаутами — в заметки) |
| Ошибки соседа | 409 → `InsufficientStock` (sku из Problem Details), 404/422 → `UnknownItem`, 5xx / нет соединения / таймаут → `InventoryUnavailable` → наш `503` + `Retry-After: 5` | Проброс чужих кодов |
| Версии API | Inventory `GET /api/stock/{sku}`: v1 `{sku, available}` (по умолчанию, устарел), v2 `{sku, name, onHand, reserved, available}`; Ordering вызывает резерв v1, склад v2 | Версия в пути |
| Резерв | `POST /api/reservations` (внутренний): идемпотентен по `orderId` (повтор → 200 и тот же резерв); строки склада блокируются `SELECT … FOR UPDATE` в порядке SKU (без дедлоков) | Оптимистическая блокировка с ретраями |
| Порядок в Ordering | Резерв (удалённый вызов) **вне** транзакции БД, затем сохранение заказа; осиротевший резерв при падении после — тема 10 | Удалённый вызов внутри транзакции |
| Gateway | Spring Cloud Gateway Server Web MVC (2025.1.3): `/api/orders/**` → ordering, `/api/stock/**` → inventory, ответный заголовок `X-Gateway`; Problem Details | WebFlux-Gateway (правило «без WebFlux»); только Envoy (нет прикладного уровня) |
| Тестовые заглушки | JDK `com.sun.net.httpserver.HttpServer` | WireMock (лишние зависимости, риск конвергенции) |
| E2E | Compose из Jib-образов + bash через Gateway | kind в CI (платформа 05 тяжела для каждого PR) |
| Деплой | `k8s/` (Kustomize): CNPG ×2, миграции Sync-хуками, 3 Deployment, HTTPRoute `shop.localtest.me`; Application в `platform/apps` за флагом | Включить сразу (образов в GHCR ещё нет) |

## 3. API

**Inventory** (`:8080`, внутри кластера `http://inventory`)

| Метод и путь | Успех | Ошибки |
|---|---|---|
| `GET /api/stock/{sku}`, `API-Version: 1` (по умолчанию) | `200 {sku, available}` + `Deprecation`, `Sunset` | `404 not-found` |
| `GET /api/stock/{sku}`, `API-Version: 2` | `200 {sku, name, onHand, reserved, available}` | `404` |
| `POST /api/reservations` `{orderId, lines:[{sku, quantity}]}` | `201` + `Location` (новый), `200` (повтор того же `orderId`) | `400 invalid-request`; `409 insufficient-stock` (+`sku`); `422 unknown-sku` (+`sku`) |

**Ordering** (`:8080`)

| Метод и путь | Успех | Ошибки |
|---|---|---|
| `POST /api/orders` `{lines:[{sku, quantity}]}` | `201` + `Location`, `{id, status: CONFIRMED, reservationId, lines, createdAt}` | `400`; `409 insufficient-stock`; `422 unknown-item`; `503 inventory-unavailable` + `Retry-After: 5` |
| `GET /api/orders/{id}` | `200` | `404` |
| `GET /api/orders/availability/{sku}` | `200 {sku, available}` (через Inventory v2) | `422`; `503` |

**Gateway** (`:8080`, снаружи `http://shop.localtest.me`): `/api/orders/**`, `/api/stock/**`; всё остальное — `404`.

Seed склада: `ANVIL-01` Anvil 10, `HAMMER-01` Smithing hammer 25, `SWORD-01` Bronze sword 2, `TONGS-01` Tongs 0.
Ограничения: `quantity` 1..1000, ≤ 20 строк, SKU `[A-Z0-9-]{1,40}`.

## 4. Тесты

| Модуль | Что |
|---|---|
| inventory | домен (резерв, нехватка); сервис на фейках (идемпотентность, неизвестный SKU, дубли SKU в строках); web slice (v1/v2, `Deprecation`, неподдерживаемая версия 400, коды ошибок); IT на Postgres: два параллельных резерва последней единицы — ровно один успех; ArchUnit |
| ordering | сервис на фейке порта; адаптер против заглушки `HttpServer`: заголовок `API-Version`, 409/422/500/закрытый порт/медленный ответ → нужные исключения; web slice (409/422/503 + `Retry-After`); IT на Postgres + заглушке; ArchUnit |
| gateway | IT: две заглушки, маршруты и заголовки (в т. ч. `API-Version` проходит), `/api/reservations` → 404, `X-Gateway` |
| стек | `scripts/e2e.sh` (Compose) |

## 5. Learning-notes

Синхронное vs асинхронное взаимодействие; HTTP Service Client (`@HttpExchange`, прокси, `@ImportHttpServices`,
Feign); таймауты (connect/read, по умолчанию — бесконечность), бюджет времени; перевод чужих ошибок, 503 vs 502
vs 504, `Retry-After`; почему без ретраев (идемпотентность, 08); удалённый вызов и транзакция БД (dual write → 10);
блокировки и дедлоки при резерве; версионирование API (header/path/media type, deprecation, Sunset);
API Gateway vs Ingress/Gateway API (прикладной vs инфраструктурный уровень, BFF в 07); K8s DNS и Service;
вопросы для собеседования.
