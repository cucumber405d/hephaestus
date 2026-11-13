# 03-containers-ci — спецификация

- **Статус:** утверждена
- **Дата:** 2026-11-13
- **Корневая спека:** [../../../../docs/superpowers/specs/2026-11-05-hephaestus-root-design.md](../../../../docs/superpowers/specs/2026-11-05-hephaestus-root-design.md)
- **Домен:** контекст Catalog из [docs/domain.md](../../../../docs/domain.md)

## 1. Цель

Научиться превращать Spring Boot-сервис в правильный OCI-образ и доставлять его через CI в реестр — фактор V
(build, release, run) из Twelve-Factor. Один и тот же облегчённый сервис Catalog собирается **тремя способами**
(Buildpacks, Jib, Dockerfile), способы сравниваются **цифрами**; отдельно изучается поведение JVM в контейнере.
Результат — образ в GHCR, на котором строится `04-k8s-basics`.

**Критерии успеха**
- Все способы сборки (раздел 4) дают рабочий образ; один и тот же `CatalogImageIT` зелёный против каждого.
- Доказано тестом: JVM видит лимиты контейнера (CPU и память), логи — JSON в stdout, SIGTERM → graceful shutdown.
- `scripts/compare-images.sh` выдаёт таблицу сравнения; результаты с выводами — в `docs/learning-notes.md`.
- `docker compose --profile app up` поднимает сервис + Postgres из готового образа.
- CI: verify + матрица образов на PR; на `main` — публикация в GHCR с неизменяемым тегом.

**Не цели:** Kubernetes (04); SBOM, Trivy, подпись Cosign (21); CDS/AOT/native (18); multi-arch-публикация;
production-grade API (это 02).
**Анти-цели:** сравнение «на глаз» без цифр; образ, который запускается от root или не ловит SIGTERM.

## 2. Решения

| Тема | Решение | Отклонено |
|---|---|---|
| Сервис | Облегчённый Catalog: 4 эндпоинта, Postgres + Flyway, Actuator | Полная копия 02 (85 файлов, фокус размывается); сборка из jar 02 (нарушает независимость) |
| Способы сборки | Один модуль `catalog`, все способы рядом, один код → честное сравнение | Модуль на способ; один способ + остальные в заметках |
| Канонический образ | **Jib** — без Docker-демона в CI, воспроизводимый | Buildpacks; Dockerfile |
| Тег публикации | `sha-<7 символов>` (неизменяемый) + `latest` | Только `latest`; semver (релизов нет) |
| Образные IT | Отдельный Maven-профиль `image-it`; обычный `verify` образ не собирает | Сборка образа в каждом `verify` (медленно, нужен Docker Hub) |
| Ожидания по способам | Общие проверки для всех; проверки лимитов CPU/heap — со своими ожиданиями для `buildpacks` (memory calculator) — расхождение и есть вывод | Подгонять Paketo под общие цифры; исключить Buildpacks из теста |
| Публикация в CI | Отдельный reusable `publish-image.yml` (переиспользуют 04+), вызывается после образных тестов; матрица сравнения — только в `03-containers-ci.yml` | Опциональные шаги внутри `build-subproject.yml` (тогда публикация шла бы до образных тестов) |

## 3. Сервис Catalog (облегчённый)

Пакет `de.ibikus.appa.hephaestus.containersci.catalog`; Maven: reactor `containers-ci`, модуль `catalog`
(`containers-ci-catalog`), parent — корневой `hephaestus-parent`.

| Метод и путь | Назначение | Успех | Ошибки |
|---|---|---|---|
| `GET /api/categories` | Список категорий по имени | `200` | — |
| `GET /api/items?category=<uuid>` | Изделия, опционально по категории, по имени, без пагинации (≤ 100) | `200` | `400` невалидный uuid |
| `GET /api/items/{id}` | Изделие | `200` | `404` |
| `POST /api/items` | Создать | `201` + `Location` | `400` валидация; `422` категории нет |

- Ресурсы — как в 02: `Category { id, name }`, `Item { id, name, description?, price { amount, currency }, categoryId, createdAt }`.
- Категории только читаются; Flyway `V3__seed_categories.sql` заводит 3 категории с фиксированными UUID.
- Ошибки — Problem Details (RFC 9457), без утечки текста исключений; `500 internal-error`.
- **Actuator:** наружу только `health` (probes `liveness`/`readiness`), `info` и собственный эндпоинт `runtime`.
- **`/actuator/runtime`** (`@Endpoint(id = "runtime")`) — то, что видит JVM: `availableProcessors`, `maxHeapBytes`
  (`Runtime.maxMemory()`), `containerMemoryLimitBytes` (из `/sys/fs/cgroup/memory.max`, `null` если файла нет
  или `max`), `gc` (имена `GarbageCollectorMXBean`), `javaVersion`, `inputArguments`.
- Код копируется из 02 и упрощается; гексагональная структура (`domain` / `application` / `adapter.in` /
  `adapter.out`) и ArchUnit-правила из 02 сохраняются. MapStruct и openapi-generator не используются — маппинг руками.
- Конфигурация — только env vars (`PORT`, `SPRING_DATASOURCE_URL|USERNAME|PASSWORD`); логи — ECS JSON в stdout;
  `server.shutdown=graceful`, `timeout-per-shutdown-phase=20s`; virtual threads.

## 4. Способы сборки образа

Локальное имя образа `hephaestus/catalog`, тег = способ. Все образы: процесс не root, порт `8080`, Java 25,
`ENTRYPOINT` в exec-форме (java = PID 1). Слои у каждого способа **свои, нативные**: Buildpacks и Dockerfile —
по `layers.idx` Spring Boot (dependencies → spring-boot-loader → snapshot-dependencies → application), Jib — свои
(dependencies → snapshot-dependencies → resources → classes, запуск `java -cp … MainClass` без loader).
Разница слоёв — часть сравнения.

| Тег | Как собирается | База | Особенности |
|---|---|---|---|
| `buildpacks` | `./mvnw -f 03-containers-ci/pom.xml -pl catalog spring-boot:build-image` | Paketo (builder `paketobuildpacks/builder-noble-java-tiny`), `BP_JVM_VERSION=25` | Memory calculator Paketo сам ставит `-Xmx`/`-Xss`/metaspace/code cache и `ActiveProcessorCount` — сравнить с `MaxRAMPercentage`; run-образ `tiny` без shell |
| `jib` | `jib:dockerBuild` (локально; конфигурация в POM неактивна, пока цель не вызвана — профиль не нужен), `jib:build` (CI → GHCR) | `eclipse-temurin:25-jre` | Без демона, `creationTime` = epoch → воспроизводимость; `user 1000:1000` |
| `dockerfile` | `docker build -t hephaestus/catalog:dockerfile catalog/` после `package` | `eclipse-temurin:25-jre` | Stage 1: `java -Djarmode=tools -jar app.jar extract --layers --launcher`; stage 2 копирует слои; `USER 1000` |
| `dockerfile-jlink` | `docker build -f catalog/Dockerfile.jlink …` | stage `eclipse-temurin:25-jdk` → `debian:stable-slim` | `jdeps --print-module-deps` + `jlink --strip-debug --no-header-files --no-man-pages`; свой минимальный JRE |

- Dockerfile собирает образ **из готового jar** (`target/*.jar`), Maven внутри Docker не запускается: контекст —
  папка модуля, корневой POM не нужен, Maven Central из Docker не нужен.
- Флаги JVM для `jib`/`dockerfile*` — `JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"`
  в образе, переопределяемые при запуске. Для `buildpacks` — то, что ставит memory calculator.
- Версии базовых образов и builder — в `<properties>` подпроекта, по тегу; pinning по digest — тема 21.
- OCI-метки: `org.opencontainers.image.source` (ссылка на репозиторий — так GHCR привязывает пакет), `revision`, `title`.
- `HEALTHCHECK` в образах не задаётся (Kubernetes его игнорирует, проверка — через probes); почему — в learning-notes.

## 5. JVM в контейнере

Опыты, закреплённые тестами и описанные в learning-notes:
| Опыт | Тест или заметки |
|---|---|
| `--cpus=1` → `availableProcessors = 1` | тест |
| `--cpus=0.5` → всё равно `1` (округление вверх) | тест |
| `JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=3` при `--cpus=1` → `3` | тест |
| `--memory=768m` → `containerMemoryLimitBytes = 805306368`, `maxHeapBytes ≈ 75 %` лимита (±10 %) | тест (кроме `buildpacks`) |
| `buildpacks` при `--memory=768m` стартует, heap = расчёт memory calculator (записать фактическое значение) | тест: только старт и лимит виден; цифры — в заметки |
| При `--cpus=1` GC = Serial; при `--cpus=2 --memory=2g` — G1 (порог: < 2 CPU **или** < ~1792 МБ → Serial) | тест (кроме `buildpacks`, там — в заметки) |
| Без лимита heap = 25 % RAM хоста | только заметки (зависит от раннера) |
| OOMKill (exit 137) против `OutOfMemoryError` + `ExitOnOutOfMemoryError` | только заметки |

Лимит для опытов и в `compose.yaml` — **768m** (512m мало для дефолтов memory calculator Paketo:
250 потоков × 1 МБ стека + 240 МБ code cache + metaspace).

## 6. Тесты

| Уровень | Что | Где запускается |
|---|---|---|
| Unit | Домен, сервисы с фейками портов, парсер `memory.max` (`max`, число, нет файла) | `verify` |
| Web slice | Коды, `Location`, Problem Details | `verify` |
| IT приложения | `@SpringBootTest(RANDOM_PORT)` + Testcontainers `postgres:17`: сценарий create → get → list by category; `/actuator/runtime` отвечает | `verify` |
| Архитектура | ArchUnit, правила как в 02 | `verify` |
| **`CatalogImageIT`** | Образ из системного свойства `catalog.image` (по умолчанию `hephaestus/catalog:jib`) + `postgres:17` в одной Testcontainers-сети: readiness `UP`; `POST` + `GET` изделия; строки stdout, начинающиеся с `{`, парсятся как JSON и среди них есть лог старта (баннер выключен `spring.main.banner-mode=off`; текстовые строки helper'ов Paketo пропускаются); не root — по полю `Config.User` из `docker inspect` (≠ пусто, `0`, `root`; одинаково для всех образов, shell не нужен); `stop` (SIGTERM) → в логе `Graceful shutdown complete`, код выхода 143 или 0 | профиль `image-it` |
| **`ContainerLimitsImageIT`** | Параметризованный по строкам таблицы §5 (`withCreateContainerCmdModifier` → `HostConfig` с `NanoCpus`/`Memory`); способ из системного свойства `catalog.flavor` (`jib` по умолчанию) включает свои ожидания | профиль `image-it` |

Профиль `image-it`: `jib:dockerBuild` привязан к `pre-integration-test`, только если `catalog.image` не задан;
failsafe с `*ImageIT`. Для других способов образ собирается заранее, затем
`./mvnw -Pimage-it -Dcatalog.image=hephaestus/catalog:buildpacks verify`. Обычный `verify` не трогает `*ImageIT`.

## 7. Compose и скрипты

- `compose.yaml`: `postgres` (без профиля, `pg_isready` healthcheck) и `catalog` (профиль `app`, образ
  `${CATALOG_IMAGE:-hephaestus/catalog:jib}`, `depends_on: postgres: condition: service_healthy`, env vars,
  `mem_limit: 768m`, `cpus: 1`, порт `8080`). Spring Boot Docker Compose support в профиле `local` поднимает
  только `postgres` (профиль `app` не активен) — запуск из IDE работает как в 02.
- `scripts/build-images.sh` — собирает все четыре образа подряд.
- `scripts/compare-images.sh` — для каждого тега: размер (`docker image inspect`), число слоёв, время холодной
  сборки, время пересборки после изменения одного класса (сколько слоёв пересобрано), время до readiness `UP`,
  `Started … in N s` из лога, память контейнера в покое (`docker stats --no-stream` — это cgroup usage, не RSS; в заметках — разница). Вывод — Markdown-таблица в stdout.
- Скрипты — bash (Git Bash / WSL на Windows, `ubuntu-latest` в CI); комментарии на английском.
- `requests.http` — сценарии для IntelliJ HTTP Client.

## 8. CI — `.github/workflows/03-containers-ci.yml`

Триггеры и paths-фильтр — как в 02 (+ `03-containers-ci/**`). Задачи:
**Новый reusable `publish-image.yml`** (`workflow_call`, входы `subproject`, `module`, `image-name`):
checkout → JDK 25 → `./mvnw -B -ntp -f <subproject>/pom.xml -pl <module> package jib:build -DskipTests`
в `ghcr.io/cucumber405d/<image-name>` с тегами `sha-<7>` и `latest`; логин `GITHUB_TOKEN`
(`permissions: contents: read, packages: write`); digest — в `$GITHUB_STEP_SUMMARY`. Его переиспользуют 04+.
`build-subproject.yml` не меняется — 01/02/_template не затронуты. Это уточнение строки §5.3 корневой спеки
(«(опц.) build image → push» в одном reusable): публикация вынесена отдельно, чтобы запускать её после
образных проверок; корневая спека правится в той же задаче.

`03-containers-ci.yml`:
1. **`verify`** — reusable `build-subproject.yml` с `subproject: 03-containers-ci`.
2. **`images`** (`needs: verify`; matrix `buildpacks`, `jib`, `dockerfile`, `dockerfile-jlink`, `fail-fast: false`):
   собрать образ своим способом → `-Pimage-it -Dcatalog.image=… -Dcatalog.flavor=…` verify → строку
   сравнения в `$GITHUB_STEP_SUMMARY`.
3. **`publish`** (`needs: images`, `if: github.event_name == 'push' && github.ref == 'refs/heads/main'`) —
   reusable `publish-image.yml` с `module: catalog`, `image-name: hephaestus/containers-ci-catalog`.
   В GHCR попадает только версия, у которой прошли образные тесты всех способов.
- `permissions` по умолчанию `contents: read`; `packages: write` — только в job публикации.
- Тег `sha-<7>` неизменяем **по соглашению**: GHCR не запрещает перезапись (перезапуск job перезапишет тот же
  образ из того же коммита — допустимо); защита неизменяемости — тема 21 (digest + подпись).
- Первый push создаёт приватный пакет; сделать его публичным — один раз руками в настройках пакета (записать в `docs/start.md`).
- Бейдж в корневом `README.md`.

## 9. Документация

`README.md`, `docs/start.md` (команды сборки всех образов, запуск compose, профили, публикация),
`docs/architecture.md` (C4 container + схема слоёв образа, Mermaid), `docs/learning-notes.md`:
Buildpacks vs Jib vs Dockerfile (таблица с цифрами); слои и кэш; воспроизводимые сборки; PID 1, exec-форма,
сигналы; non-root; JVM container awareness, `MaxRAMPercentage` vs memory calculator, выбор GC; OOMKill vs OOME;
jlink и минимальный JRE; теги vs digest, неизменяемые теги; GHCR и права `GITHUB_TOKEN`; почему нет `HEALTHCHECK`;
Docker Desktop / WSL2 на Windows; вопросы уровня собеседования.

## 10. Риски

| Риск | Мера |
|---|---|
| Memory calculator Paketo не укладывается в лимит / ставит `ActiveProcessorCount` по хосту | Ожидания `buildpacks` свои (§5); фактические значения — в заметки; при отказе старта — `BPL_JVM_THREAD_COUNT` |
| Paketo builder без Java 25 или с другим именем | Проверить на старте; builder и `BP_JVM_VERSION` — в properties |
| Jib 3.5.2 и class-файлы Java 25 | Явная база `eclipse-temurin:25-jre`; при проблеме — обновить Jib |
| `jdeps` не видит модули из-за reflection Spring | Дополнить список модулей вручную (`java.sql`, `jdk.unsupported`, `java.management`, `java.naming`, `jdk.management`…; `jdk.crypto.ec` не нужен — с JDK 22 EC в `java.base`); проверяется `CatalogImageIT` |
| Флаки по времени (startup, RSS) | Цифры только в отчёте скрипта, тесты на время не завязаны |
| Docker Hub rate limit в CI | Базовые образы общие для матрицы; при проблеме — `docker login` или зеркало `mirror.gcr.io` |
| Агент без Maven Central | Сборку и образы запускает Dmitry или CI |
