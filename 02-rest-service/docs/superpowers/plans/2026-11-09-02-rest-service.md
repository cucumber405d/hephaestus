# 02-rest-service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Сервис Catalog (Item, Category) — production-grade REST по контракту `catalog-api.yaml`, гексагональная архитектура, JPA + PostgreSQL + Flyway, полный набор тестов.

**Architecture:** Reactor `02-rest-service` → модуль `catalog`. openapi-generator генерирует `CategoriesApi`/`ItemsApi` + DTO; контроллеры в `adapter.in.web` их реализуют и маппят (MapStruct) в домен; сервисы `application` работают через порты; `adapter.out.persistence` — JPA-сущности + Spring Data + MapStruct. Домен — чистые records без зависимостей.

**Tech Stack:** Java 25, Spring Boot 4.1.2 (Web MVC, Data JPA, Actuator), PostgreSQL 17, Flyway, openapi-generator-maven-plugin, MapStruct, springdoc-openapi, swagger-request-validator-mockmvc, Testcontainers, ArchUnit, JUnit 6/AssertJ.

**Spec:** `02-rest-service/docs/superpowers/specs/2026-11-09-02-rest-service-design.md`

**Execution environment:** Maven Central недоступен из среды агента: `mvnw` запускает Dmitry (JDK 25 по умолчанию) или CI. Нужен запущенный Docker (Testcontainers).

## Global Constraints

- Пакет `de.ibikus.appa.hephaestus.restservice.catalog`; reactor `rest-service`, модуль `catalog` → `rest-service-catalog`.
- JDK 25, Spotless palantir как в корне; комментарии и javadoc только на английском.
- Домен зависит только от JDK; generated API (`..adapter.in.web.api..`) используется только из `adapter.in.web`.
- Ошибки — `application/problem+json`, `type` = `https://hephaestus.ibikus.de/problems/<slug>`; слаги: `invalid-request` 400, `not-found` 404, `duplicate-category` 409, `category-in-use` 409, `idempotency-conflict` 409, `precondition-failed` 412, `unsupported-media-type` 415, `unknown-category` 422, `precondition-required` 428, `unsupported-api-version` 400.
- ETag = `"<version>"` (strong, версия JPA с 0).
- `Idempotency-Key` 1..100 символов, хранится 24 ч.
- Поиск: `size` 1..100 (по умолчанию 20), `page` ≥ 0, сортировка только `name|price|createdAt` с префиксом `-`.
- Hibernate `ddl-auto: validate`, `open-in-view: false`; схема только через Flyway.
- RED — компилируемые заглушки, падение на assertions (не на компиляции).
- Каждая задача: impact-файл `impacts/impact_YYYY_MM_DD_HH.MM.SS.md` + раздел в `docs/learning-notes.md` в том же коммите.
- Версии внешних зависимостей (openapi-generator, MapStruct, springdoc, swagger-request-validator) — последний release на момент Task 1, фиксируются в `<properties>` reactor-POM; проверка `versions:display-property-updates`.

## Review Focus

1. **Unicode и регистр в `q`** (`?q=БРОНЗ`, `?q=%`) → поиск без учёта регистра, `%`/`_` экранируются, а не работают как wildcard (Task 6).
2. **Цена с > 2 знаками** (`12.345`) или отрицательная → `400`, а не молчаливое округление (Task 3).
3. **Одинаковый `Idempotency-Key` в двух одновременных POST** → создаётся ровно одно изделие, второй запрос получает тот же ответ или `409`, не `500` (Task 5).
4. **Невалидный `If-Match`** (`*`, `W/"1"`, `"abc"`, список) → `412`, не `500`; `*` трактуется как «любая версия» запрещён — `412` (Task 7).
5. **Имя категории с разным регистром/пробелами** (`"Swords"` и `" swords "`) → дубликат `409` (нормализация `strip` + уникальность без учёта регистра) (Task 4).

---

### Task 1: Каркас, Flyway-схема, Testcontainers, CI

**Files:**
- Create: `02-rest-service/pom.xml` (reactor, parent `../pom.xml`, `<properties>` версий внешних библиотек), `02-rest-service/catalog/pom.xml`
- Create: `catalog/src/main/java/.../catalog/CatalogApplication.java`, `catalog/src/main/resources/application.yaml`, `application-local.yaml`
- Create: `catalog/src/main/resources/db/migration/V1__category.sql`, `V2__item.sql`, `V3__idempotency_key.sql` (DDL дословно из спеки §5; уникальность имени категории — `create unique index category_name_ci on category (lower(name))` вместо `unique` на колонке, RF5)
- Create: `catalog/src/test/java/.../catalog/{PostgresTestcontainers,SchemaMigrationIT,HexagonalArchitectureTest}.java`
- Create: `02-rest-service/compose.yaml`, `README.md`, `docs/{start,architecture,learning-notes}.md`
- Create: `.github/workflows/02-rest-service.yml` (копия `_template.yml`, без `java-version`); бейдж в корневой README

**Interfaces:**
- Produces: `@TestConfiguration(proxyBeanMethods=false) class PostgresTestcontainers` с `@Bean @ServiceConnection PostgreSQLContainer postgres()` на образе `postgres:17` — импортируется всеми `*IT` и `@DataJpaTest`.
- Зависимости модуля: `spring-boot-starter-webmvc`, `-data-jpa`, `-actuator`, `-validation`, `flyway-core`, `flyway-database-postgresql`, `postgresql` (runtime), `mapstruct`, `springdoc-openapi-starter-webmvc-ui`, `spring-boot-docker-compose` (optional); test: `spring-boot-starter-webmvc-test`, `-data-jpa-test`, `spring-boot-testcontainers`, `testcontainers-postgresql`, `archunit-junit5`, `swagger-request-validator-mockmvc`. Annotation processor: `mapstruct-processor` (`-Amapstruct.defaultComponentModel=spring`, `-Amapstruct.unmappedTargetPolicy=ERROR`).
- `application.yaml`: `spring.jpa.hibernate.ddl-auto=validate`, `spring.jpa.open-in-view=false`, `spring.threads.virtual.enabled=true`, `spring.docker.compose.enabled=false`, structured ECS-логи, probes, graceful shutdown 20s, `server.port=${PORT:8080}`; `application-local.yaml`: `spring.docker.compose.enabled=true`, `logging.structured.format.console=` (человекочитаемые логи).

- [ ] **Step 1: Тест (RED через пустые миграции-заглушки `V1..V3` с `select 1;`)**
```java
// SchemaMigrationIT (@SpringBootTest, @Import(PostgresTestcontainers.class))
jdbc.queryForList("select table_name from information_schema.tables where table_schema='public'", String.class)
    contains "category", "item", "idempotency_key", "flyway_schema_history"
// category name unique case-insensitive:
insert category ('…','Swords',0) then ('…','swords',0) → DataIntegrityViolationException
// item.price_amount check: insert -1 → DataIntegrityViolationException
```
`HexagonalArchitectureTest` — копия из `_template` + правило: классы в `..adapter.in.web.api..` доступны только из `..adapter.in.web..` (`optionalLayer` для пустых слоёв).
- [ ] **Step 2:** `.\mvnw.cmd -f 02-rest-service/pom.xml verify` → FAIL на assertions (`category` и др. отсутствуют).
- [ ] **Step 3:** Настоящий DDL в `V1..V3`.
- [ ] **Step 4:** `spotless:apply verify` → PASS; `versions:display-property-updates` → версии свежие.
- [ ] **Step 5:** Docs (start: `docker compose`, `spring-boot:run -Dspring-boot.run.profiles=local`), impact, commit `feat(02): scaffold, Flyway schema, Testcontainers, CI`.

---

### Task 2: Контракт `catalog-api.yaml` и генерация API

**Files:**
- Create: `catalog/src/main/resources/static/catalog-api.yaml` (OpenAPI 3.1; всё из спеки §3: пути, параметры, заголовки `ETag`/`Location`/`If-Match`/`Idempotency-Key`/`API-Version`, схемы `Category`, `NewCategory`, `Item`, `ItemInput`, `Money`, `ItemPage`, `Problem` (+ `errors[]`), ответы ошибок с `application/problem+json`)
- Modify: `catalog/pom.xml` — `openapi-generator-maven-plugin`: `generatorName=spring`, `inputSpec=${project.basedir}/src/main/resources/static/catalog-api.yaml`, `apiPackage=…adapter.in.web.api`, `modelPackage=…adapter.in.web.api.model`, `configOptions`: `interfaceOnly=true`, `useSpringBoot3=true`, `useJakartaEe=true`, `useTags=true`, `openApiNullable=false`, `skipDefaultInterface=true`, `useResponseEntity=true`, `documentationProvider=none`
- Test: `catalog/src/test/java/.../adapter/in/web/GeneratedApiTest.java`

**Interfaces:**
- Produces (generated): `CategoriesApi` (`createCategory`, `listCategories`, `getCategory`, `deleteCategory`), `ItemsApi` (`createItem`, `searchItems`, `getItem`, `replaceItem`, `patchItem`, `deleteItem`); модели `CategoryDto`, `NewCategoryDto`, `ItemDto`, `ItemInputDto`, `MoneyDto`, `ItemPageDto` (суффикс `Dto` через `modelNameSuffix=Dto`).
- PATCH: тело `application/merge-patch+json` описано как `type: object` (свободный объект) — генератор отдаёт `Map<String, Object>`; допустимые ключи перечислены в `description`. Причина: POJO теряет разницу «ключ отсутствует» vs `null`.

- [ ] **Step 1: Тест**
```java
assertThat(Arrays.stream(ItemsApi.class.getMethods()).map(Method::getName))
    .contains("createItem","searchItems","getItem","replaceItem","patchItem","deleteItem");
assertThat(Arrays.stream(CategoriesApi.class.getMethods()).map(Method::getName))
    .contains("createCategory","listCategories","getCategory","deleteCategory");
// контракт парсится без ошибок: new OpenAPIV3Parser().readLocation(<path>, null, opts).getMessages() isEmpty
```
- [ ] **Step 2:** RED: контракт-заглушка только с `info` → тест падает (методов нет).
- [ ] **Step 3:** Полный контракт → PASS. При несовместимости генератора с Boot 4 — Ruling в ledger (см. риски спеки).
- [ ] **Step 4:** Docs (contract-first vs code-first), impact, commit `feat(02): OpenAPI contract and generated API`.

---

### Task 3: Домен

**Files:** `domain/{ItemId,CategoryId,Ids,Money,Category,Item,NewItem,SortField,SortOrder,ItemSearchCriteria,PageRequest,Page}.java`, исключения `domain/{CatalogException,InvalidValueException,ItemNotFoundException,CategoryNotFoundException,UnknownCategoryException,DuplicateCategoryNameException,CategoryInUseException,VersionConflictException}.java` + unit-тесты.

**Interfaces (Produces):**
- `record ItemId(UUID value)`, `record CategoryId(UUID value)` + `static newId()` → `Ids.uuidV7()`.
- `final class Ids { static UUID uuidV7() }` — 48 бит Unix-миллисекунд, версия 7, вариант RFC 9562, остальное — `SecureRandom`.
- `record Money(BigDecimal amount, String currency)`: `amount ≥ 0`, `scale ≤ 2` (иначе `InvalidValueException`, без округления — RF2), нормализация `setScale(2)`; `currency` `[A-Z]{3}`.
- `record Category(CategoryId id, String name, long version)`: `name` → `strip()`, длина 1..100.
- `record NewItem(String name, String description, Money price, CategoryId categoryId)`: `name` strip 1..200, `description` null или ≤ 2000.
- `record Item(ItemId id, String name, String description, Money price, CategoryId categoryId, Instant createdAt, long version)`; `static Item create(NewItem, Instant now)`; `Item replace(NewItem)` — меняет поля, сохраняет `id`, `createdAt`, `version`.
- `enum SortField { NAME, PRICE, CREATED_AT }`; `record SortOrder(SortField field, boolean descending)`; `static SortOrder parse(String)` — `"name"`, `"-price"`, `"createdAt"`; иначе `InvalidValueException`.
- `record ItemSearchCriteria(CategoryId category, BigDecimal minPrice, BigDecimal maxPrice, String nameContains, List<SortOrder> sort)` — поля nullable, `minPrice > maxPrice` → `InvalidValueException`; `sort` по умолчанию `[NAME asc]`.
- `record PageRequest(int page, int size)` — `page ≥ 0`, `1 ≤ size ≤ 100`; `record Page<T>(List<T> content, int page, int size, long totalElements)` + `int totalPages()`.
- `CatalogException extends RuntimeException`; остальные — его наследники с понятным `getMessage()`.

- [ ] **Step 1: Тесты** (заглушки-records без валидации компилируются)
```java
Money.of("12.345","EUR") → InvalidValueException; Money.of("-1","EUR") → InvalidValueException
Money.of("12.5","EUR").amount().toPlainString() == "12.50"
new Category(id, "  Swords ", 0).name() == "Swords"; name "" или 101 символ → InvalidValueException
SortOrder.parse("-price") == (PRICE, true); parse("weight") → InvalidValueException
new ItemSearchCriteria(null, 10, 5, null, null) → InvalidValueException
new PageRequest(0, 101) → InvalidValueException; new Page<>(List.of(), 0, 20, 41).totalPages() == 3
var a = Ids.uuidV7(); var b = Ids.uuidV7(); a.version()==7; a.variant()==2; a.compareTo(b) < 0 после Thread.sleep(2)
item.replace(newItem): id, createdAt, version сохранены, остальные поля новые
```
- [ ] **Step 2:** RED → реализация → PASS.
- [ ] **Step 3:** Docs (UUIDv7 vs sequence, почему домен без JPA), impact, commit `feat(02): domain model`.

---

### Task 4: Категории — сквозной срез

**Files:**
- `application/port/in/ManageCategoriesUseCase.java`, `application/port/out/CategoryRepository.java`, `application/service/CategoryService.java`
- `adapter/out/persistence/{CategoryJpaEntity,CategorySpringDataRepository,JpaCategoryRepository,PersistenceMapper}.java`
- `adapter/in/web/{CategoriesController,WebMapper,ProblemDetailsAdvice,ETags}.java`
- Tests: `application/service/CategoryServiceTest` (фейк `InMemoryCategoryRepository` + `InMemoryItemRepository` в test-sources), `adapter/out/persistence/JpaCategoryRepositoryIT` (`@DataJpaTest` + Testcontainers), `adapter/in/web/CategoriesControllerTest` (`@WebMvcTest` + фейк use case)

**Interfaces:**
- `ManageCategoriesUseCase`: `Category create(String name)`, `List<Category> list()` (по имени), `Category get(CategoryId)`, `void delete(CategoryId)`.
- `CategoryRepository`: `Category save(Category)`, `Optional<Category> findById(CategoryId)`, `List<Category> findAllOrderedByName()`, `boolean existsByNameIgnoreCase(String)`, `void delete(CategoryId)`.
- `ItemRepository` (часть, нужна для `delete`): `boolean existsByCategory(CategoryId)` — объявляется здесь, остальное в Task 5.
- `ETags`: `static String of(long version)` → `"\"" + version + "\""`; `static long parseIfMatch(String header)` → `VersionConflictException` для `*`, `W/…`, нечислового, списка (RF4).
- `ProblemDetailsAdvice` (`@RestControllerAdvice`): маппинг исключений → статус + `type`-слаг из Global Constraints; `MethodArgumentNotValidException`/`HttpMessageNotReadableException`/`InvalidValueException` → 400 + `errors[]`; `DataIntegrityViolationException` (уникальность/FK) → 409 по имени ограничения.
- `PersistenceMapper` (MapStruct): `CategoryJpaEntity ↔ Category`.

- [ ] **Step 1: Тесты**
```java
// CategoryServiceTest
create("Swords") then create(" swords ") → DuplicateCategoryNameException            // RF5
delete(id with items) → CategoryInUseException; delete(unknown) → CategoryNotFoundException
// JpaCategoryRepositoryIT
save → version 0; save again changed → version 1; findAllOrderedByName order
delete category referenced by an item (insert item via JdbcTemplate) → DataIntegrityViolationException
// CategoriesControllerTest
POST {"name":"Swords"} → 201, Location ends "/api/categories/{id}", ETag "\"0\""
POST {"name":""} → 400 problem type …/invalid-request, errors[0].field == "name"
POST duplicate → 409 …/duplicate-category; DELETE in use → 409 …/category-in-use; GET unknown → 404 …/not-found
```
- [ ] **Step 2:** RED (заглушки возвращают `null`/ничего не бросают) → реализация → PASS.
- [ ] **Step 3:** Docs (Problem Details, почему проверка + FK), impact, commit `feat(02): categories end to end`.

---

### Task 5: Изделия — создание, чтение, идемпотентность

**Files:**
- `application/port/in/ManageItemsUseCase.java` (часть: `create`, `get`), `application/port/out/{ItemRepository,IdempotencyStore,StoredResponse}.java`, `application/service/ItemService.java`
- `adapter/out/persistence/{ItemJpaEntity,ItemSpringDataRepository,JpaItemRepository,IdempotencyKeyJpaEntity,JpaIdempotencyStore}.java`
- `adapter/in/web/{ItemsController,IdempotencyFilter}.java`
- Tests: `ItemServiceTest`, `JpaItemRepositoryIT`, `ItemsControllerTest`, `IdempotencyFilterIT` (`@SpringBootTest` + MockMvc + Testcontainers)

**Interfaces:**
- `ManageItemsUseCase`: `Item create(NewItem)` (несуществующая категория → `UnknownCategoryException`), `Item get(ItemId)`.
- `ItemRepository`: `Item save(Item)`, `Optional<Item> findById(ItemId)`, `boolean existsByCategory(CategoryId)`, `void delete(ItemId)` (Task 7), `Page<Item> search(ItemSearchCriteria, PageRequest)` (Task 6).
- `record StoredResponse(String requestHash, int status, String body, String location, String etag, Instant createdAt)`; `IdempotencyStore`: `Optional<StoredResponse> find(String key)`, `boolean saveIfAbsent(String key, StoredResponse)` (insert … on conflict do nothing → `false`, RF3), `int deleteOlderThan(Instant)`.
- `IdempotencyFilter` (`OncePerRequestFilter`, только `POST /api/items`): нет/пустой/>100 ключ → 400; ключ найден и `requestHash` = SHA-256 тела → воспроизвести сохранённый ответ (статус, тело, `Location`, `ETag`); hash другой → 409 `idempotency-conflict`; иначе выполнить запрос (кэширующая обёртка тела) и при `2xx` — `saveIfAbsent`; если `saveIfAbsent` вернул `false` (параллельный дубль) — ответ из стора.

- [ ] **Step 1: Тесты**
```java
// ItemsControllerTest: POST valid → 201 + Location + ETag "\"0\""; POST unknown category → 422 …/unknown-category
// GET /api/items/{id} → 200 + ETag; unknown → 404
// IdempotencyFilterIT
POST without Idempotency-Key → 400
POST key=k1 body A twice → both 201, same Location, item count == 1
POST key=k1 body B → 409 …/idempotency-conflict
two concurrent POST key=k2 (two threads, CountDownLatch start) → both non-5xx, item count == 1      // RF3
// JpaItemRepositoryIT: save/find round trip incl. Money scale 2 and createdAt precision (micros)
```
- [ ] **Step 2:** RED → реализация → PASS.
- [ ] **Step 3:** Docs (идемпотентность, POST vs PUT), impact, commit `feat(02): items create/read, idempotency`.

---

### Task 6: Поиск изделий

**Files:** `application/port/in/SearchItemsQuery.java`, `ItemService` (implements), `adapter/out/persistence/ItemSpecifications.java`, `JpaItemRepository.search`, `ItemsController.searchItems`; tests `ItemSearchIT` (`@DataJpaTest`), `ItemsControllerTest` (поиск).

**Interfaces:**
- `SearchItemsQuery`: `Page<Item> search(ItemSearchCriteria, PageRequest)`.
- `ItemSpecifications`: `static Specification<ItemJpaEntity> matching(ItemSearchCriteria)` — `nameContains`: `lower(name) like lower(:q) escape '\'` с экранированием `\`, `%`, `_` (RF1); сортировка `SortField → "name"|"priceAmount"|"createdAt"`, затем `id` для стабильности.
- Web: `sort` — список строк (`?sort=price&sort=-name` и `?sort=price,-name`), `size`/`page` из контракта.

- [ ] **Step 1: Тесты**
```java
// ItemSearchIT (5 изделий в 2 категориях, цены 5..50)
category=A → только A; minPrice=10,maxPrice=30 → границы включительно; q="бронз" находит "Бронзовый меч"
q="%" → 0 результатов (нет изделия с '%' в имени)                                         // RF1
sort=-price → цены по убыванию; size=2,page=1 → 2 элемента, totalElements 5, totalPages 3
statistics.clear(); search(...); statistics.getPrepareStatementCount() == 2                // spec §5
// ItemsControllerTest: sort=weight → 400; size=0 → 400; minPrice>maxPrice → 400
```
- [ ] **Step 2:** RED → реализация → PASS.
- [ ] **Step 3:** Docs (N+1 и доказательство, offset vs keyset), impact, commit `feat(02): item search`.

---

### Task 7: Изменение и удаление — ETag, If-Match, Merge Patch

**Files:** `ManageItemsUseCase` (+`replace`, `patch`, `delete`), `application/port/in/{ItemPatch,PatchValue}.java`, `ItemService`, `ItemsController.{replaceItem,patchItem,deleteItem}`, `adapter/in/web/MergePatchParser.java`; tests `ItemServiceTest`, `ItemsControllerTest`, `MergePatchParserTest`, `OptimisticLockingIT` (`@DataJpaTest`).

**Interfaces:**
- `Item replace(ItemId, long expectedVersion, NewItem)`, `Item patch(ItemId, long expectedVersion, ItemPatch)`, `void delete(ItemId, long expectedVersion)` — версия ≠ текущей → `VersionConflictException`.
- `sealed interface PatchValue<T>` = `Absent<T>` | `Remove<T>` | `Set<T>(T value)`; `record ItemPatch(PatchValue<String> name, PatchValue<String> description, PatchValue<Money> price, PatchValue<CategoryId> categoryId)`.
- `MergePatchParser.parse(Map<String,Object>) -> ItemPatch`: ключи вне `name|description|price|categoryId` (включая `id`, `createdAt`) → `InvalidValueException`; `null` для `name|price|categoryId` → `InvalidValueException`; `price` — merge внутрь объекта (`{"price":{"amount":"9.99"}}` меняет только сумму).
- Web: нет `If-Match` → 428; `ETags.parseIfMatch` → 412; PATCH с другим `Content-Type` → 415; `ObjectOptimisticLockingFailureException` → 412 в `ProblemDetailsAdvice`.

- [ ] **Step 1: Тесты**
```java
// ItemsControllerTest
PUT without If-Match → 428 …/precondition-required; PUT If-Match "\"5\"" (current 0) → 412 …/precondition-failed
PUT If-Match "*" / "W/\"0\"" / "\"abc\"" → 412                                                // RF4
PUT ok → 200 + ETag "\"1\""; PATCH Content-Type application/json → 415
PATCH {"description":null} → description removed; PATCH {"name":null} → 400; PATCH {"id":"…"} → 400
PATCH {"price":{"amount":"9.99"}} → amount 9.99, currency unchanged
DELETE If-Match ok → 204; GET afterwards → 404
// OptimisticLockingIT: two TransactionTemplate reads of same row, both update → second ObjectOptimisticLockingFailureException
// web slice: fake service throws ObjectOptimisticLockingFailureException → 412
```
- [ ] **Step 2:** RED → реализация → PASS.
- [ ] **Step 3:** Docs (оптимистическая блокировка через HTTP, Merge Patch vs JSON Patch), impact, commit `feat(02): replace, merge patch, delete with ETags`.

---

### Task 8: Версия API, контрактные сценарии, эксплуатация

**Files:**
- `application.yaml`: API versioning (`spring.mvc.apiversion.use.header=API-Version`, `supported=1`, `default=1`)
- `adapter/out/persistence/IdempotencyCleanup.java` (`@Scheduled(cron="0 0 * * * *")` → `deleteOlderThan(now-24h)`), `@EnableScheduling` в конфигурации
- springdoc: `springdoc.swagger-ui.url=/catalog-api.yaml`, `springdoc.api-docs.enabled=false`
- `02-rest-service/requests.http`
- Tests: `CatalogScenarioIT` (`@SpringBootTest` + `@AutoConfigureMockMvc` + Testcontainers; каждый ответ проверяется `openApi().isValid("static/catalog-api.yaml")`), `ApiVersionTest`, `IdempotencyCleanupIT`

**Interfaces:** Consumes всё из Tasks 4–7.

- [ ] **Step 1: Тесты**
```java
// CatalogScenarioIT (each response .andExpect(openApi().isValid(spec)))
create category → create item (Idempotency-Key) → search → PATCH with ETag → stale PUT → 412 → DELETE → 204
// ApiVersionTest: GET /api/categories with API-Version: 2 → 400 …/unsupported-api-version; without header → 200
// IdempotencyCleanupIT: key created 25h ago (JdbcTemplate) + fresh key → cleanup → only fresh remains
// mutation check (manual, recorded in ledger): CategoriesController returns 200 instead of 201 → CatalogScenarioIT red
```
- [ ] **Step 2:** RED → реализация → PASS; ручной прогон `spring-boot:run` (profile `local`) + `requests.http` + Swagger UI на `/swagger-ui.html`.
- [ ] **Step 3:** Docs (полные learning-notes §8 спеки, architecture.md с Mermaid-диаграммой слоёв), impact, commit `feat(02): API versioning, contract scenarios, ops`. Push → PR → CI.
