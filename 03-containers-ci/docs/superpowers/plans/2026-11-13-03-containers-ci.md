# 03-containers-ci Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Облегчённый Catalog, собранный в OCI-образ четырьмя способами (Buildpacks, Jib, Dockerfile, Dockerfile+jlink), с образными тестами, опытами «JVM в контейнере», сравнением цифрами, Compose и CI с публикацией Jib-образа в GHCR.

**Architecture:** Reactor `03-containers-ci` → модуль `catalog` (гексагональный, код скопирован из 02 и упрощён). Все способы сборки работают от одного jar/модуля. Образные тесты (`*ImageIT`) живут в пакете `image`, запускаются только профилем `image-it` против образа из `-Dcatalog.image`. CI: verify → matrix образов → reusable `publish-image.yml`.

**Tech Stack:** Java 25, Spring Boot 4.1.2 (Web MVC, Data JPA, Actuator, Flyway), PostgreSQL 17, Jib 3.5.2, Paketo Buildpacks, Docker, Testcontainers, ArchUnit, JUnit 6/AssertJ, bash, GitHub Actions.

**Spec:** `03-containers-ci/docs/superpowers/specs/2026-11-13-03-containers-ci-design.md`

**Execution environment:** Maven Central недоступен из среды агента: `mvnw` и `docker` запускает Dmitry (JDK 25, Docker Desktop) или CI. Агент пишет код и команды, Dmitry присылает вывод.

## Global Constraints

- Пакет `de.ibikus.appa.hephaestus.containersci.catalog`; reactor `containers-ci` (`03-containers-ci/pom.xml`, parent `../pom.xml`), модуль `catalog` → `containers-ci-catalog`, `<finalName>catalog</finalName>` (jar = `catalog/target/catalog.jar`).
- JDK 25, Spotless palantir как в корне; комментарии и javadoc — только на английском (также POM, YAML, Dockerfile, bash).
- Гексагональная структура `domain` / `application` / `adapter.in` / `adapter.out`; домен зависит только от JDK; ArchUnit-правила как в 02 (`HexagonalArchitectureTest`).
- Ошибки — `application/problem+json`, `type` = `https://hephaestus.ibikus.de/problems/<slug>`; слаги: `invalid-request` 400, `not-found` 404, `unknown-category` 422, `internal-error` 500 (без текста исключения).
- Суммы — строки в JSON (`"12.50"`), строгий разбор `Money.of` из 02 (≤ 2 знака, ≤ 10 цифр до точки, без экспоненты).
- Конфигурация только env vars: `PORT` (8080), `SPRING_DATASOURCE_URL|USERNAME|PASSWORD`; профили только `local`, `test`.
- Логи ECS JSON в stdout, `spring.main.banner-mode=off`; `server.shutdown=graceful`, `timeout-per-shutdown-phase=20s`; virtual threads.
- Actuator наружу: `health`, `info`, `runtime`; probes включены.
- Локальные образы: `hephaestus/catalog:{buildpacks|jib|dockerfile|dockerfile-jlink}`; все — не root (`Config.User` ≠ пусто/`0`/`root`), порт 8080, `ENTRYPOINT` exec-форма.
- `JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"` в образах `jib`, `dockerfile`, `dockerfile-jlink`; в `buildpacks` — не задаётся.
- Версии баз — в `<properties>` reactor-POM: `jre.image=eclipse-temurin:25-jre`, `jdk.image=eclipse-temurin:25-jdk`, `jlink.runtime.image=debian:stable-slim`, `paketo.builder=paketobuildpacks/builder-noble-java-tiny`; плюс `image.revision=unknown`.
- OCI-метки во всех образах: `org.opencontainers.image.source=https://github.com/cucumber405d/hephaestus`, `org.opencontainers.image.revision=${image.revision}`, `org.opencontainers.image.title=containers-ci-catalog`.
- Лимит памяти в опытах и `compose.yaml` — `768m`.
- GHCR: `ghcr.io/cucumber405d/hephaestus/containers-ci-catalog`, теги `sha-<7>` и `latest`, только `push` в `main`, только после образных тестов.
- Обычный `verify` не собирает образ и не запускает `*ImageIT` (failsafe `excludes **/*ImageIT.java`).
- Каждая задача: impact-файл `impacts/impact_YYYY_MM_DD_HH.MM.SS.md` (`TZ=Europe/Berlin date +%Y_%m_%d_%H.%M.%S`) + раздел в `03-containers-ci/docs/learning-notes.md` в том же коммите.
- RED — компилируемые заглушки, падение на assertions.

## Review Focus

1. **Нет БД при старте контейнера** (Postgres недоступен) → контейнер завершается с ненулевым кодом за ≤ 60 с с понятной ошибкой в логе, а не висит и не «зелёный» (Task 6, `CatalogImageIT.exitsWhenDatabaseIsUnreachable`).
2. **`PORT=9090`** → сервис слушает 9090 в контейнере (фактор VII), readiness `UP` на 9090 (Task 6, `CatalogImageIT.honoursPortVariable`).
3. **`memory.max` = `"max\n"`, число с `\n`, мусор или файла нет (cgroup v1, Windows-хост без контейнера)** → `containerMemoryLimitBytes = null`, эндпоинт не падает (Task 5, `CgroupMemoryLimitTest`).
4. **`GET /api/items?category=not-a-uuid`** и **`GET /api/items/not-a-uuid`** → `400 invalid-request`, не `500` (Task 4, `ItemsControllerTest`).
5. **`POST /api/items` с несуществующей категорией** (в т. ч. гонка: проверка прошла, FK упал) → `422 unknown-category`, не `500` (Task 3 `JpaItemRepositoryIT.unknownCategoryOnForeignKey`, Task 4 web slice).

---

### Task 1: Каркас, схема, Testcontainers, CI verify

**Files:**
- Create: `03-containers-ci/pom.xml`, `03-containers-ci/catalog/pom.xml`, `03-containers-ci/README.md`, `03-containers-ci/docs/start.md`, `03-containers-ci/docs/learning-notes.md`, `03-containers-ci/compose.yaml` (пока только `postgres` с healthcheck `pg_isready`)
- Create: `catalog/src/main/java/.../CatalogApplication.java`, `catalog/src/main/resources/application.yaml`, `application-local.yaml`, `db/migration/V1__category.sql`, `V2__item.sql`, `V3__seed_categories.sql`
- Create: `catalog/src/test/java/.../PostgresTestcontainers.java`, `SchemaMigrationIT.java`
- Create: `.github/workflows/03-containers-ci.yml` (пока только job `verify`); Modify: `README.md` (бейдж)

**Interfaces:**
- Produces: схема `category(id, name, version)`, `item(...)` — как V1/V2 в 02 (без idempotency); seed:
  `01900000-0000-7000-8000-000000000001` Armor, `…0002` Tools, `…0003` Weapons.
- Produces: `PostgresTestcontainers` (`@TestConfiguration`, `@ServiceConnection PostgreSQLContainer("postgres:17")`).

- [ ] **Step 1:** Reactor POM (properties из Global Constraints, `<modules>catalog</modules>`); module POM: webmvc, data-jpa, validation, actuator, flyway + flyway-database-postgresql, postgresql, docker-compose (optional), тесты — webmvc-test, data-jpa-test, testcontainers, testcontainers-postgresql, archunit; failsafe `excludes **/*ImageIT.java`.
- [ ] **Step 2:** `application.yaml` — по образцу 02 (без apiversion/springdoc/jackson non_null оставить), плюс `banner-mode: off`, `management.endpoints.web.exposure.include: health,info,runtime`; `docker.compose.enabled: false`, в `application-local.yaml` — `true`.
- [ ] **Step 3: Failing test** `SchemaMigrationIT.seedsThreeCategories`: `select name from category order by name` = `["Armor","Tools","Weapons"]`; `migratesItemTable`: таблица `item` есть, FK `item_category_fk` есть.
- [ ] **Step 4:** Миграции V1–V3 (V1/V2 копия 02).
- [ ] **Step 5: Verify** `./mvnw -f 03-containers-ci/pom.xml verify` → BUILD SUCCESS, `SchemaMigrationIT` 2 tests.
- [ ] **Step 6:** `03-containers-ci.yml`: триггеры/paths/concurrency как `02-rest-service.yml` (+ `publish-image.yml` в paths), job `verify` → `build-subproject.yml`, `subproject: 03-containers-ci`. Бейдж в корневом README. `docs/start.md`: сборка, запуск `-Dspring-boot.run.profiles=local`.
- [ ] **Step 7: Commit** `feat(03): scaffold, Flyway schema with seed, Testcontainers, CI`

### Task 2: Домен и application

**Files:**
- Create: `domain/` — `Money`, `Item`, `NewItem`, `Category`, `ItemId`, `CategoryId`, `Ids`, `Texts`, `CatalogException`, `InvalidValueException`, `ItemNotFoundException`, `UnknownCategoryException` (копии 02; `Item`/`Category` без `version` и `replace`)
- Create: `application/port/in/ManageItemsUseCase`, `ListCategoriesUseCase`; `application/port/out/ItemRepository`, `CategoryRepository`; `application/service/ItemService`, `CategoryService`
- Test: `domain/MoneyTest`, `ItemTest`; `application/fakes/InMemoryItemRepository`, `InMemoryCategoryRepository`; `application/service/ItemServiceTest`, `CategoryServiceTest`

**Interfaces:**
- Produces: `record Item(ItemId id, String name, String description, Money price, CategoryId categoryId, Instant createdAt)`; `Item.create(NewItem, Instant)`.
- Produces: `ManageItemsUseCase { Item create(NewItem data); Item get(ItemId id); List<Item> list(Optional<CategoryId> category); }`; `ListCategoriesUseCase { List<Category> list(); }`.
- Produces: `ItemRepository { Item insert(Item); Optional<Item> findById(ItemId); List<Item> findAll(Optional<CategoryId> category, int limit); }`; `CategoryRepository { boolean exists(CategoryId); List<Category> findAllOrderedByName(); }`.
- Produces: `ItemService.LIST_LIMIT = 100`; `ItemService(ItemRepository, CategoryRepository, Clock)`.

- [ ] **Step 1: Failing tests** `MoneyTest` (копия 02: `"12.345"` → `InvalidValueException`, `"1E+5"` → `InvalidValueException`, `"-1"` → `InvalidValueException`, `"12.5"` → amount `12.50`); `ItemServiceTest.createRejectsUnknownCategory` → `UnknownCategoryException`; `createStampsClockTime` → `createdAt == Instant.parse("2026-11-13T10:00:00Z")` с fixed clock; `getMissingThrowsNotFound`; `listFiltersByCategoryAndSortsByName`; `listIsCappedAt100` (101 изделие → 100); `CategoryServiceTest.listsByName`.
- [ ] **Step 2: Run** → FAIL на assertions.
- [ ] **Step 3:** Реализация (сервисы `@Transactional` на `create`, `readOnly` на чтении).
- [ ] **Step 4: Run** `./mvnw -f 03-containers-ci/pom.xml verify` → PASS.
- [ ] **Step 5: Commit** `feat(03): slim domain and application services`

### Task 3: Persistence-адаптер

**Files:**
- Create: `adapter/out/persistence/` — `ItemJpaEntity`, `CategoryJpaEntity` (с `@Version`, как в схеме), `ItemSpringDataRepository`, `CategorySpringDataRepository`, `JpaItemRepository`, `JpaCategoryRepository`, ручной `PersistenceMapper` (static методы, без MapStruct)
- Test: `adapter/out/persistence/JpaItemRepositoryIT`, `JpaCategoryRepositoryIT` (`@DataJpaTest` + `@Import(PostgresTestcontainers.class)`)

**Interfaces:**
- Consumes: порты Task 2.
- Produces: `JpaItemRepository implements ItemRepository`; при `DataIntegrityViolationException` с ограничением `item_category_fk` → `UnknownCategoryException`, прочие — пробрасываются.

- [ ] **Step 1: Failing tests** `JpaItemRepositoryIT.insertAndFindRoundTrip` (все поля равны, `price.amount` = `12.50`); `findAllByCategoryOrdersByName`; `findAllRespectsLimit`; `unknownCategoryOnForeignKey` (insert с случайным `CategoryId`, `flush`) → `UnknownCategoryException`; `JpaCategoryRepositoryIT.existsSeededCategory`, `listsSeedByName`.
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3:** Реализация; `findAll` — `Limit.of(limit)` + `Sort.by("name")`.
- [ ] **Step 4: Run** verify → PASS.
- [ ] **Step 5: Commit** `feat(03): JPA persistence adapter`

### Task 4: Web-адаптер, сценарий, ArchUnit

**Files:**
- Create: `adapter/in/web/` — `ItemsController`, `CategoriesController`, `ItemRequest`/`PriceJson`/`ItemResponse`/`CategoryResponse` (records), `WebMapper` (static), `ProblemDetailsAdvice`
- Test: `adapter/in/web/ItemsControllerTest`, `CategoriesControllerTest`, `ProblemDetailsAdviceTest` (`@WebMvcTest`, `MockMvcTester`, фейки), `CatalogScenarioIT`, `HexagonalArchitectureTest`

**Interfaces:**
- Produces: `GET /api/categories` → `[{id,name}]`; `GET /api/items?category=` → `[ItemResponse]`; `GET /api/items/{id}`; `POST /api/items` → `201`, `Location: /api/items/{id}`.
- Produces JSON: `ItemResponse(UUID id, String name, String description, PriceJson price, UUID categoryId, Instant createdAt)`, `PriceJson(String amount, String currency)`; `description` опускается, если `null`.

- [ ] **Step 1: Failing tests** `ItemsControllerTest`: `createReturns201WithLocation`; `createInvalidPriceIs400` (`"12.345"`) с `type` …`/invalid-request`; `createUnknownCategoryIs422`; `getMissingIs404`; `getMalformedIdIs400`; `listMalformedCategoryIs400`; `unexpectedErrorIs500WithoutDetails` (фейк бросает `IllegalStateException("secret")` → `detail` не содержит `secret`); `CatalogScenarioIT` (`RANDOM_PORT`, `RestClient`): create → get → list by Weapons содержит изделие, list by Armor не содержит; `HexagonalArchitectureTest` — правила 02 без generated api.
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3:** Реализация (копия `ProblemDetailsAdvice` 02, урезанная до 4 слагов; `MethodArgumentTypeMismatchException` и `HttpMessageNotReadableException` → 400).
- [ ] **Step 4: Run** verify → PASS. Ручная проверка `requests.http` (создать файл: list categories, create, get, list by category).
- [ ] **Step 5: Commit** `feat(03): REST adapter, problem details, scenario and architecture tests`

### Task 5: Эндпоинт `runtime`

**Files:**
- Create: `adapter/in/actuator/RuntimeEndpoint.java`, `RuntimeInfo.java`, `CgroupMemoryLimit.java`
- Test: `adapter/in/actuator/CgroupMemoryLimitTest`, `RuntimeEndpointIT` (`RANDOM_PORT` + Postgres)

**Interfaces:**
- Produces: `@Endpoint(id = "runtime")` `@ReadOperation RuntimeInfo runtime()`;
  `record RuntimeInfo(int availableProcessors, long maxHeapBytes, Long containerMemoryLimitBytes, List<String> gc, String javaVersion, List<String> inputArguments)`;
  `CgroupMemoryLimit.parse(String content): OptionalLong`, `CgroupMemoryLimit.read(Path file): OptionalLong` (`Path.of("/sys/fs/cgroup/memory.max")` по умолчанию; конструктор `RuntimeEndpoint(Path memoryMaxFile)` для тестов).

- [ ] **Step 1: Failing tests** `CgroupMemoryLimitTest`: `"805306368\n"` → `805306368`; `"max\n"` → empty; `""`, `"abc"`, `"-1"` → empty; `read(missingFile)` → empty; `read(unreadableDir)` → empty без исключения. `RuntimeEndpointIT.exposesRuntime`: `GET /actuator/runtime` 200, `availableProcessors == Runtime.getRuntime().availableProcessors()`, `gc` не пуст, `javaVersion` начинается с `25`.
- [ ] **Step 2: Run** → FAIL. **Step 3:** Реализация (`ManagementFactory.getGarbageCollectorMXBeans()`, `getRuntimeMXBean().getInputArguments()`). **Step 4: Run** verify → PASS.
- [ ] **Step 5:** learning-notes: раздел «Что видит JVM» (заготовка таблицы §5 спеки). **Commit** `feat(03): runtime actuator endpoint with cgroup memory limit`

### Task 6: Jib-образ и `CatalogImageIT`

**Files:**
- Modify: `catalog/pom.xml` — `jib-maven-plugin` (config всегда, цели только по вызову): `from.image=${jre.image}`, `to.image=hephaestus/catalog:jib`, `container.user=1000:1000`, `ports=8080`, `environment.JAVA_TOOL_OPTIONS`, `labels` (Global Constraints); профиль `image-it`: failsafe `includes **/*ImageIT.java`, `excludes` пусто; `jib:dockerBuild` на `pre-integration-test` (пропуск через стандартный `-Djib.skip=true`)
- Modify: `compose.yaml` — сервис `catalog` (профиль `app`, `${CATALOG_IMAGE:-hephaestus/catalog:jib}`, env, `mem_limit: 768m`, `cpus: 1`, `8080:8080`, `depends_on` healthy)
- Create test: `image/CatalogContainers.java`, `image/ImageFlavor.java`, `image/CatalogImageIT.java`

**Interfaces:**
- Produces: `enum ImageFlavor { BUILDPACKS, JIB, DOCKERFILE, DOCKERFILE_JLINK; boolean jvmFlagsFromImage(); static ImageFlavor current(); }` (`-Dcatalog.flavor`, по умолчанию `jib`, `dockerfile-jlink` ↔ `DOCKERFILE_JLINK`; `jvmFlagsFromImage()` = `this != BUILDPACKS`).
- Produces: `CatalogContainers.image()` (`-Dcatalog.image`, по умолчанию `hephaestus/catalog:jib`), `postgres(Network)` (alias `postgres`, db/user/password `catalog`), `catalog(Network, String image)` → `GenericContainer` с env datasource, `withExposedPorts(8080)`, wait `/actuator/health/readiness` 200, timeout 120 s.
- Graceful stop: `DockerClient.stopContainerCmd(id).withTimeout(30)` (не `container.stop()` — тот шлёт SIGKILL), затем `inspectContainerCmd` → `State.ExitCode`.

- [ ] **Step 1: Failing tests** `CatalogImageIT`: `startsAndServesApi` (readiness UP; `POST` изделия в Weapons → 201; `GET` по `Location` → 200); `logsAsJson` (среди строк stdout, начинающихся с `{`, есть объект с `message` ∋ `Started CatalogApplication`); `runsAsNonRoot` (`inspectImageCmd(image).getConfig().getUser()` ∉ {`""`, `null`, `0`, `root`} и не начинается с `0:`); `shutsDownGracefullyOnSigterm` (лог ∋ `Graceful shutdown complete`, exit ∈ {0, 143}); `honoursPortVariable` (`PORT=9090`, exposed 9090, readiness UP); `exitsWhenDatabaseIsUnreachable` (datasource → `jdbc:postgresql://nowhere:5432/catalog`, без wait; за 60 s `State.Running == false`, `ExitCode != 0`).
- [ ] **Step 2: Run** `./mvnw -f 03-containers-ci/pom.xml -Pimage-it verify` → образ `hephaestus/catalog:jib` собран; тесты сначала FAIL там, где не хватает конфигурации (например, user/`banner-mode`), затем доводим.
- [ ] **Step 3: Run** → 6 tests PASS; `docker compose -f 03-containers-ci/compose.yaml --profile app up -d` → `curl localhost:8080/api/categories` 200.
- [ ] **Step 4:** learning-notes: Jib (слои, без демона, `creationTime` epoch), PID 1/SIGTERM, почему SIGKILL в Testcontainers. **Commit** `feat(03): Jib image, image integration tests, full-stack compose`

### Task 7: `ContainerLimitsImageIT`

**Files:**
- Create test: `image/ContainerLimitsImageIT.java`

**Interfaces:**
- Consumes: `CatalogContainers`, `ImageFlavor`, `RuntimeInfo` JSON из Task 5.
- Лимиты: `withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withNanoCPUs(...).withMemory(...))`; `MiB768 = 805_306_368L`.

- [ ] **Step 1: Failing tests** (каждый — свой контейнер; ожидания по `ImageFlavor.current()`):
  - `oneCpu` (1.0 CPU, 768m): все — `containerMemoryLimitBytes == 805306368`; если `jvmFlagsFromImage()` — `availableProcessors == 1`, `maxHeapBytes` ∈ [0.65 × 805306368, 0.85 × 805306368], `gc` ∋ `MarkSweepCompact`.
  - `halfCpuRoundsUp` (0.5 CPU): если `jvmFlagsFromImage()` — `availableProcessors == 1`, иначе `assumeTrue(false)`.
  - `activeProcessorCountOverrides` (1 CPU, `JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=3`): если `jvmFlagsFromImage()` — `3`.
  - `twoCpusTwoGigabytesSelectG1` (2 CPU, 2g): если `jvmFlagsFromImage()` — `gc` ∋ `G1 Young Generation`.
  - Для `BUILDPACKS` в `oneCpu` дополнительно печатать `RuntimeInfo` в лог теста (цифры — для learning-notes).
- [ ] **Step 2: Run** `-Pimage-it verify` → FAIL до реализации теста/парсинга, затем PASS (Jib).
- [ ] **Step 3:** learning-notes: таблица §5 с фактическими значениями Jib. **Commit** `test(03): container limits awareness`

### Task 8: Buildpacks, Dockerfile, Dockerfile.jlink

**Files:**
- Modify: `catalog/pom.xml` — `spring-boot-maven-plugin` `<image>`: `name=hephaestus/catalog:buildpacks`, `builder=${paketo.builder}`, `env BP_JVM_VERSION=25`, `BP_OCI_SOURCE`/`BP_IMAGE_LABELS` для меток
- Create: `catalog/Dockerfile`, `catalog/Dockerfile.jlink`, `catalog/.dockerignore` (всё, кроме `target/catalog.jar`), `scripts/build-images.sh`

**Interfaces:**
- `Dockerfile`: `ARG JRE_IMAGE`, stage `extract` (`java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted`), final — `COPY` слоёв по одному в порядке dependencies → spring-boot-loader → snapshot-dependencies → application, `USER 1000:1000`, `ENV JAVA_TOOL_OPTIONS`, `LABEL`, `ENTRYPOINT ["java","org.springframework.boot.loader.launch.JarLauncher"]`.
- `Dockerfile.jlink`: `ARG JDK_IMAGE`, `ARG RUNTIME_IMAGE`; jdk-stage: extract → `jdeps --ignore-missing-deps --multi-release 25 --print-module-deps --class-path 'extracted/dependencies/BOOT-INF/lib/*' …` + модули из спеки §10 → `jlink --strip-debug --no-header-files --no-man-pages --compress zip-6 --output /opt/jre`; final: `debian:stable-slim`, `useradd -u 1000`, `/opt/jre/bin/java` в `PATH`.
- `scripts/build-images.sh [flavor…]` (по умолчанию все 4): `package -DskipTests` один раз, затем build-image / `jib:dockerBuild` / `docker build --build-arg … -t hephaestus/catalog:<flavor> catalog/`; `set -euo pipefail`; запуск из любой папки (`cd "$(dirname "$0")/.."`).

- [ ] **Step 1: Run** `scripts/build-images.sh` → 4 образа в `docker images hephaestus/catalog`.
- [ ] **Step 2: Run** для каждого: `./mvnw -f 03-containers-ci/pom.xml -Pimage-it -Djib.skip=true -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Dcatalog.image=hephaestus/catalog:<flavor> -Dcatalog.flavor=<flavor> verify` → PASS для `dockerfile`, `dockerfile-jlink`, `buildpacks` (FAIL `dockerfile-jlink` из-за недостающих модулей — дополнить список, повторить).
- [ ] **Step 3:** learning-notes: Buildpacks (memory calculator, фактические цифры из `oneCpu`), Dockerfile (layertools/extract, exec-форма, `.dockerignore`), jlink (jdeps, почему модули вручную), почему нет `HEALTHCHECK`. **Commit** `feat(03): Buildpacks, layered Dockerfile and jlink Dockerfile images`

### Task 9: Сравнение образов

**Files:**
- Create: `scripts/compare-images.sh`

**Interfaces:**
- `scripts/compare-images.sh [flavor…]` → Markdown-таблица в stdout: `| image | size MB | layers | cold build s | rebuild s | rebuilt layers | ready s | started in s | memory MiB |`.
- Rebuild: временный `catalog/src/main/java/.../BuildMarker.java` с случайной константой, `trap` удаляет его; «rebuilt layers» = число различий в `RootFS.Layers` до/после.
- Ready: `docker run -d` с Postgres из `compose.yaml` (`docker compose up -d postgres`, сеть compose), опрос `/actuator/health/readiness` раз в 200 ms; started in — из JSON-лога `Started CatalogApplication in N seconds`; memory — `docker stats --no-stream --format '{{.MemUsage}}'` через 10 s покоя.

- [ ] **Step 1: Run** `scripts/compare-images.sh` → таблица из 4 строк, все ячейки заполнены числами.
- [ ] **Step 2:** learning-notes: таблица + выводы (размер, кэш слоёв, воспроизводимость: два `jib:dockerBuild` подряд → одинаковый image id; два `build-image` подряд → одинаковый; `docker build` → разный, почему). **Commit** `feat(03): image comparison script and results`

### Task 10: CI — матрица и публикация в GHCR

**Files:**
- Create: `.github/workflows/publish-image.yml`
- Modify: `.github/workflows/03-containers-ci.yml`, `docs/superpowers/specs/2026-11-05-hephaestus-root-design.md` (уже сделано при спеке — проверить), `03-containers-ci/docs/start.md`

**Interfaces:**
- `publish-image.yml` (`workflow_call`, inputs `subproject`, `module`, `image-name`; `permissions: contents: read, packages: write`): checkout → setup-java 25 + maven cache → `docker/login-action@v3` (`ghcr.io`, `${{ github.actor }}`, `GITHUB_TOKEN`) → `./mvnw -B -ntp -f "$SUBPROJECT/pom.xml" -pl "$MODULE" package jib:build -DskipTests -Djib.to.image=ghcr.io/cucumber405d/$IMAGE:sha-${GITHUB_SHA::7} -Djib.to.tags=latest -Dimage.revision=$GITHUB_SHA` → `cat $SUBPROJECT/$MODULE/target/jib-image.digest >> $GITHUB_STEP_SUMMARY`. Все `${{ inputs.* }}` — через `env`, не в `run` напрямую.
- `03-containers-ci.yml`: `verify` → `images` (`needs: verify`, matrix 4 flavor, `fail-fast: false`; `scripts/build-images.sh $FLAVOR`, затем команда image-it из Task 8 (для `jib` без `-Djib.skip`), затем `scripts/compare-images.sh $FLAVOR >> $GITHUB_STEP_SUMMARY`) → `publish` (`needs: images`, `if: github.event_name == 'push' && github.ref == 'refs/heads/main'`, `permissions: contents: read, packages: write`, `uses: ./.github/workflows/publish-image.yml`). Workflow-level `permissions: contents: read`.

- [ ] **Step 1:** Push ветки, открыть PR → `verify` и 4 `images` зелёные, `publish` пропущен (`skipped`).
- [ ] **Step 2:** После merge → `publish` зелёный, в summary digest; `docker pull ghcr.io/cucumber405d/hephaestus/containers-ci-catalog:sha-<7>` работает после того, как Dmitry сделал пакет публичным.
- [ ] **Step 3:** `docs/start.md`: публикация, сделать пакет публичным (Package settings → Change visibility), pull. learning-notes: GHCR, `GITHUB_TOKEN` и `packages: write`, теги vs digest, «неизменяемость по соглашению». **Commit** `ci(03): image matrix and GHCR publishing via reusable workflow`

### Task 11: Документация

**Files:**
- Modify: `03-containers-ci/README.md`, `docs/start.md`, `docs/learning-notes.md`; Create: `docs/architecture.md`

- [ ] **Step 1:** `architecture.md`: C4 container (Mermaid: catalog → postgres; CI → GHCR), схема слоёв каждого способа, ключевые решения со ссылкой на спеку.
- [ ] **Step 2:** learning-notes — проверить покрытие всех тем §9 спеки + 10 вопросов уровня собеседования с краткими ответами; README — цель, чему учит, ссылки.
- [ ] **Step 3: Verify** все ссылки в docs относительные и существуют (`grep -o '](\([^)]*\))'` + проверка файлов). **Commit** `docs(03): architecture, learning notes, README`
