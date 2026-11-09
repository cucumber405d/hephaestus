# 02-rest-service — спецификация

- **Статус:** ждёт ревью
- **Дата:** 2026-11-09
- **Корневая спека:** [../../../../docs/superpowers/specs/2026-11-05-hephaestus-root-design.md](../../../../docs/superpowers/specs/2026-11-05-hephaestus-root-design.md)
- **Домен:** контекст Catalog из [docs/domain.md](../../../../docs/domain.md)

## 1. Цель

Сервис **Catalog** «по канону» — эталон для всех следующих сервисов: production-grade REST на Spring Boot 4,
contract-first OpenAPI, гексагональная архитектура, JPA + PostgreSQL + Flyway, тесты на настоящей БД.

**Критерии успеха**
- Все эндпоинты раздела 3 реализованы и соответствуют `catalog-api.yaml` (проверено контрактным тестом).
- Оптимистическая блокировка, идемпотентность POST, JSON Merge Patch, версионирование API доказаны тестами.
- Поиск страницы изделий выполняет ровно 2 SQL-запроса (доказано тестом).
- `./mvnw -f 02-rest-service/pom.xml verify` зелёный локально и в CI (JDK 25, Testcontainers).
- `docs/learning-notes.md` покрывает темы раздела 8.

**Не цели:** Docker-образ (03), Kubernetes (04), безопасность (07), кэширование (08), HATEOAS, вторая версия API.

## 2. Решения

| Тема | Решение | Отклонено |
|---|---|---|
| Объём API | Production-grade: фильтры, сортировка, пагинация, ETag/If-Match, PATCH, Idempotency-Key, версионирование | Только CRUD; + HATEOAS/HTTP-кэш |
| Contract-first | `catalog-api.yaml` руками; openapi-generator (`interfaceOnly`) генерирует `*Api` + DTO; плюс контрактный тест ответов (`swagger-request-validator`) | Только контрактный тест; code-first springdoc |
| Модель | Два агрегата `Item` и `Category`, ссылка по id | Один агрегат; + JSONB-атрибуты |
| Архитектура | Строгая гексагональная: домен без JPA, отдельные `*JpaEntity`, MapStruct на границах | JPA-сущности = домен; Spring Data JDBC |

## 3. API (`/api`, OpenAPI 3.1)

| Метод и путь | Назначение | Успех | Ошибки |
|---|---|---|---|
| `POST /categories` | Создать категорию | `201` + `Location` + `ETag` | `400`; `409` дубликат имени |
| `GET /categories` | Список (по имени) | `200` | — |
| `GET /categories/{id}` | Категория | `200` + `ETag` | `404` |
| `DELETE /categories/{id}` | Удалить | `204` | `404`; `409` есть изделия |
| `POST /items` | Создать; заголовок `Idempotency-Key` обязателен | `201` + `Location` + `ETag`; повтор с тем же ключом и телом — тот же ответ | `400` (нет ключа/невалидно); `422` категории нет; `409` тот же ключ, другое тело |
| `GET /items` | Поиск: `category`, `minPrice`, `maxPrice`, `q` (подстрока имени, без учёта регистра), `sort` (`name`, `price`, `createdAt`, префикс `-` = убывание), `page` (с 0), `size` (1–100, по умолчанию 20) | `200`: `content`, `page`, `size`, `totalElements`, `totalPages` | `400` неизвестное поле сортировки, `size` вне диапазона, `minPrice > maxPrice` |
| `GET /items/{id}` | Изделие | `200` + `ETag` | `404` |
| `PUT /items/{id}` | Полная замена; `If-Match` обязателен | `200` + новый `ETag` | `400`; `404`; `412` ETag устарел; `428` нет `If-Match`; `422` категории нет |
| `PATCH /items/{id}` | JSON Merge Patch (RFC 7386), `Content-Type: application/merge-patch+json`, `If-Match` | `200` + новый `ETag` | как `PUT` + `415` другой Content-Type |
| `DELETE /items/{id}` | Удалить; `If-Match` | `204` | `404`; `412`; `428` |

**Ресурсы.** `Category { id: uuid, name: string(1..100) }`.
`Item { id: uuid, name: string(1..200), description: string?(..2000), price: { amount: decimal ≥ 0 (2 знака), currency: ISO 4217 }, categoryId: uuid, createdAt: date-time }`.

**Сквозные правила**
- **ETag** = `"<version>"` (strong), версия — JPA `@Version`. `If-Match` с другим значением → `412`.
- **Ошибки** — RFC 9457 Problem Details (`application/problem+json`): `type` (`https://hephaestus.ibikus.de/problems/<slug>`), `title`, `status`, `detail`, `instance`; для `400` валидации — `errors: [{field, message}]`.
- **Версия API** — механизм API versioning Spring Framework 7, заголовок `API-Version`; поддерживается `1`, по умолчанию `1`; неподдерживаемая → `400`.
- **Idempotency-Key** — строка 1..100; хранится с SHA-256 тела запроса и сохранённым ответом (статус, тело, `Location`, `ETag`) 24 ч; очистка — `@Scheduled` раз в час.
- Merge Patch: `null` удаляет необязательное поле (`description`), для обязательного — `400`; `id` и `createdAt` в патче недопустимы — `400`.

## 4. Архитектура

Пакет `de.ibikus.appa.hephaestus.restservice.catalog`; Maven: reactor `rest-service`, модуль `catalog` (`rest-service-catalog`).

```
domain/                    Item, Category, ItemId, CategoryId, Money, ItemSearchCriteria, PageRequest, Page<T>,
                           ItemNotFoundException, CategoryNotFoundException, CategoryInUseException,
                           DuplicateCategoryNameException, VersionConflictException
application/port/in/       ManageItemsUseCase, SearchItemsQuery, ManageCategoriesUseCase
application/port/out/      ItemRepository, CategoryRepository, IdempotencyStore
application/service/       ItemService, CategoryService (@Transactional)
adapter/in/web/            ItemsController, CategoriesController (implements generated api), WebMapper (MapStruct),
                           ProblemDetailsAdvice, IdempotencyFilter (servlet filter на `POST /api/items`), ETags
adapter/in/web/api/        сгенерировано openapi-generator (target/generated-sources) — не в git
adapter/out/persistence/   ItemJpaEntity, CategoryJpaEntity, IdempotencyKeyJpaEntity, Spring Data репозитории,
                           ItemSpecifications, PersistenceMapper (MapStruct), JpaItemRepository и т. д.
```

**ArchUnit:** правила `_template` (домен — только JDK и свой пакет; слои внутрь; `adapter.in` ↔ `adapter.out` не зависят друг от друга) + generated `..adapter.in.web.api..` используется только из `adapter.in.web`.

## 5. Данные (Flyway, PostgreSQL 17)

- `V1__category.sql`: `category(id uuid pk, name varchar(100) not null unique, version bigint not null)`.
- `V2__item.sql`: `item(id uuid pk, name varchar(200) not null, description varchar(2000), price_amount numeric(12,2) not null check (price_amount >= 0), price_currency char(3) not null, category_id uuid not null references category(id), created_at timestamptz not null, version bigint not null)`; индексы `item(category_id)`, `item(price_amount)`.
- `V3__idempotency_key.sql`: `idempotency_key(key varchar(100) pk, request_hash char(64) not null, response_status int not null, response_body jsonb, location text, etag text, created_at timestamptz not null)`; индекс по `created_at`.
- Hibernate `ddl-auto: validate`; `open-in-view: false`.
- ID — UUIDv7, генерирует приложение.
- Гонки: проверка `If-Match` в сервисе + `@Version` → `ObjectOptimisticLockingFailureException` → `412`; удаление категории — проверка в сервисе + FK → `DataIntegrityViolationException` → `409`; уникальность имени — unique-индекс → `409`.
- Поиск — JPA `Specification` + `Pageable`; сортировка только по белому списку.
- N+1 исключён by design (изделие отдаёт `categoryId`); поиск страницы = 2 запроса (`select` + `count`).

## 6. Тесты

| Уровень | Что | Инструменты |
|---|---|---|
| Unit | Домен (`Money`, инварианты), сервисы с in-memory фейками портов | JUnit 6, AssertJ |
| Web slice | Коды, заголовки (`Location`, `ETag`, `412`, `428`, `415`), Problem Details, `API-Version` | `@WebMvcTest`, `MockMvcTester`, фейки сервисов |
| Persistence slice | Миграции, `Specification`, сортировка, `@Version`-гонка, FK → `409`, unique → `409`, **2 SQL на поиск** (Hibernate `Statistics`) | `@DataJpaTest` + Testcontainers `postgres:17`, `@ServiceConnection` |
| Contract | Ответы сценарных IT валидируются против `catalog-api.yaml` | `swagger-request-validator` |
| IT | Полные сценарии: создание → поиск → PATCH c ETag → `412`; повтор POST с тем же ключом; два параллельных `PUT` — один `200`, другой `412` | `@SpringBootTest(RANDOM_PORT)` + Testcontainers |
| Архитектура | Гексагональные правила + изоляция generated api | ArchUnit |

RED — через компилируемые заглушки (падение на assertions). Контрактный тест проверяется мутацией (например, `200` вместо `201`).

## 7. Запуск и CI

- JDK 25; Spotless как в корне (palantir).
- `compose.yaml` (Postgres 17) + Spring Boot Docker Compose support в профиле `local`.
- Swagger UI (springdoc) показывает исходный `catalog-api.yaml`.
- `requests.http` — сценарии для IntelliJ HTTP Client, включая `If-Match`/`412` и `Idempotency-Key`.
- CI: `.github/workflows/02-rest-service.yml` → reusable `build-subproject.yml` (JDK 25).

## 8. Learning-notes

Contract-first vs code-first; идемпотентность и `Idempotency-Key` (POST vs PUT); оптимистическая блокировка
(`@Version` → ETag → `412`/`428`); JSON Merge Patch vs JSON Patch; N+1 и его доказательство тестом;
`open-in-view`; UUIDv7 vs sequence; Problem Details; почему JPA-сущности ≠ домен; когда hexagonal — оверинжиниринг;
пагинация offset vs keyset.

## 9. Риски

| Риск | Мера |
|---|---|
| openapi-generator и Spring Boot 4 / Jakarta | Фиксируем версию, `useSpringBoot3`/`useJakartaEe`; при несовместимости — шаблоны или откат на вариант «контрактный тест» |
| MapStruct + generated POJO + records | Явные маппинги, `unmappedTargetPolicy=ERROR` |
| Флаки параллельного теста `PUT` | Детерминированная гонка через `CountDownLatch` в фейковом/реальном сервисе, не таймеры |
| Testcontainers в CI | Docker на `ubuntu-latest` есть; локально — Docker Desktop |
