# Hephaestus — корневая спецификация

- **Статус:** утверждено; корневой каркас реализован (план `plans/2026-11-05-root-scaffold.md`)
- **Дата:** 2026-11-05
- **Автор:** Dmitry
- **Связанные документы:** [Каталог технологий](../../technology-catalog.md)

## 1. Цель

Hephaestus — учебный монорепозиторий для глубокого освоения разработки микросервисов на современной Java
в Kubernetes с соблюдением принципов Twelve-Factor App (и «15 factors»). Одновременно это портфолио
для немецкого рынка senior/architect-позиций.

**Критерии успеха**
- Каждая тема из ядра (★) и углубления (◆) каталога закрыта работающим, протестированным подпроектом.
- Любой подпроект собирается и тестируется одной командой без кластера; деплой-подпроекты поднимаются в общий локальный кластер.
- По каждому подпроекту есть `learning-notes.md` с выводами и вопросами уровня собеседования.

**Не цели**
- Не одна сквозная продакшн-система: подпроекты независимы.
- Не облачный деплой, не Quarkus/Dapr/Spring AI/собственный оператор — возможная третья волна.

## 2. Ключевые решения

| Решение | Выбор | Альтернативы (отклонены) |
|---|---|---|
| Форма проекта | Набор независимых учебных подпроектов, «учебный маршрут» с нумерацией | Сквозная система; нарезка по технологиям |
| Охват | Волна 1 — ядро ★, волна 2 — углубление ◆ | Только ★; всё вкл. ○ |
| Инфраструктура | Гибрид: Testcontainers/Compose локально, общая платформа `platform/` для K8s | Только общая; каждый сам по себе |
| Сборка | Maven, корневой `hephaestus-parent` (BOM + pluginManagement), не агрегатор | Gradle; смешанный |
| Хостинг / CI | GitHub, GitHub Actions, GHCR | GitLab CI |
| Java | 25 LTS базово; 27 (+preview) только в `01-modern-java` | — |
| Основной фреймворк | Spring Boot 4 / Spring Framework 7, Web MVC на virtual threads | WebFlux как основной |
| Домен | Общий мини-домен «Мастерская Гефеста» | Свой домен на тему; Bürgeramt |
| Пакеты / groupId | `de.ibikus.appa.hephaestus`; пакеты `de.ibikus.appa.hephaestus.<subproject>.<service>` | — |

## 3. Структура репозитория

```
hephaestus/
├── pom.xml                  ← hephaestus-parent (только parent, не агрегатор)
├── README.md
├── CLAUDE.md                ← правила для агента
├── impacts/                 ← impact-файлы задач
├── docs/
│   ├── start.md             ← команды сборки/запуска
│   ├── technology-catalog.md
│   ├── domain.md            ← модель домена
│   ├── adr/NNNN-*.md        ← MADR
│   └── superpowers/specs|plans/
├── platform/                ← общий kind-кластер (наполняется в 05)
├── 01-modern-java/ … 22-quality-gates/
└── .github/workflows/
    ├── build-subproject.yml ← reusable workflow (workflow_call, вход: путь подпроекта)
    └── NN-<name>.yml        ← вызов с paths-фильтром на подпроект
```

### 3.1 Корневой POM (`hephaestus-parent`)
- `groupId de.ibikus.appa.hephaestus`, `artifactId hephaestus-parent`, `packaging pom`.
- Наследуется от `spring-boot-starter-parent` (см. [ADR 0001](../../adr/0001-parent-inherits-spring-boot-starter-parent.md)); версии Testcontainers/JUnit/Mockito — из BOM Boot; явные версии остального (ArchUnit, Jib, Spotless, …) — в `<properties>`.
- Активные плагины: enforcer (JDK 25+, Maven 3.9+), spotless (check), jacoco (report), failsafe (`*IT`); `maven.compiler.release` = `java.version` = 25.
- Подпроекты наследуют через `<relativePath>../pom.xml</relativePath>`; сборка подпроекта: `mvn -f NN-<name>/pom.xml verify`.

### 3.2 Структура подпроекта
```
NN-<name>/
├── pom.xml                  ← parent ../pom.xml; <modules> — сервисы подпроекта
├── README.md                ← цель, чему учит, ссылки
├── docs/
│   ├── start.md
│   ├── architecture.md      ← C4 (Mermaid), решения
│   ├── learning-notes.md    ← выводы, подводные камни, вопросы для собеседования
│   └── superpowers/specs|plans/
├── <service>/               ← Maven-модуль
├── compose.yaml             ← локальные backing services
└── k8s/                     ← Kustomize base + overlays/local (с 04)
```

## 4. Подпроекты

### Волна 1 — ядро ★

| # | Подпроект | Тема | Ключевые технологии | Домен |
|---|---|---|---|---|
| 01 | `01-modern-java` | Современная Java | Records, sealed, pattern matching, virtual threads, structured concurrency, scoped values, Gatherers | Расчёт заказа, параллельные запросы поставщикам |
| 02 | `02-rest-service` | Сервис по канону | Spring Boot 4, OpenAPI contract-first, Problem Details, JPA/Postgres, Flyway, гексагональная архитектура, ArchUnit, Testcontainers, Actuator | Catalog |
| 03 | `03-containers-ci` | Образы и CI | Buildpacks vs Jib vs Dockerfile, JVM в контейнере, Compose, GitHub Actions, GHCR | Catalog |
| 04 | `04-k8s-basics` | Kubernetes руками | kind, Deployment/Service, ConfigMap/Secret, probes, requests/limits, graceful shutdown, Flyway-Job, Kustomize | Catalog |
| 05 | `05-platform-gitops` | Общая платформа | `platform/`: Helm, Argo CD (App-of-Apps), Gateway API, CloudNativePG, Strimzi Kafka, Keycloak, Redis, LGTM-стек | — |
| 06 | `06-service-communication` | Сервис ↔ сервис | Spring Cloud Gateway, K8s DNS, `@HttpExchange`/RestClient, версионирование API | Ordering → Inventory |
| 07 | `07-security` | AuthN/AuthZ | Keycloak, OAuth2 Resource Server, Auth Code + PKCE (BFF), Client Credentials, token propagation, RBAC, NetworkPolicy | Customer, роли |
| 08 | `08-resilience` | Устойчивость | Resilience4j, Redis-кэш, Toxiproxy | Inventory ← Supplier |
| 09 | `09-events-kafka` | Event-driven | Spring Kafka, партиции, consumer groups, DLT, idempotent consumer | Ordering → Notification |
| 10 | `10-outbox-saga` | Распределённые транзакции | Outbox (Spring Modulith Events), Saga: хореография и оркестрация, компенсации | Ordering → Payment → Inventory → Delivery |
| 11 | `11-observability` | Наблюдаемость | OpenTelemetry, Micrometer, Prometheus, Grafana, Loki, Tempo, JSON-логи, trace через Kafka | Сага из 10 |
| 12 | `12-scaling-operations` | Эксплуатация, аудит 12 факторов | HPA, KEDA (Kafka lag), rolling update, PDB, Gatling | Пиковая распродажа |

### Волна 2 — углубление ◆

| # | Подпроект | Тема | Ключевые технологии |
|---|---|---|---|
| 13 | `13-modular-monolith` | От монолита к сервисам | Spring Modulith, Strangler Fig, ACL |
| 14 | `14-contracts-grpc` | Контракты | gRPC/Protobuf, Apicurio Schema Registry + Avro, AsyncAPI, Spring Cloud Contract |
| 15 | `15-cdc-debezium` | CDC | Debezium outbox, сравнение с 10 |
| 16 | `16-cqrs-event-sourcing` | CQRS/ES | Event store, проекции, replay |
| 17 | `17-workflow-orchestration` | Оркестрация движком | Temporal (альтернатива — Camunda 8) |
| 18 | `18-fast-startup` | Старт и производительность | GraalVM native, CDS/AOT cache, JFR/JMC, G1 vs ZGC |
| 19 | `19-service-mesh` | Service mesh | Istio Ambient, mTLS, traffic shifting, mesh vs Resilience4j |
| 20 | `20-progressive-delivery` | Безопасные релизы | Argo Rollouts, SLO/SLI, Alertmanager, Skaffold/Tilt |
| 21 | `21-supply-chain-secrets` | Безопасность поставок | CycloneDX SBOM, Trivy, Cosign, External Secrets + OpenBao, cert-manager, Kyverno |
| 22 | `22-quality-gates` | Качество | PIT, Error Prone + NullAway, Sonar, Renovate, E2E на kind в CI |

**Порядок:** строго по номерам внутри волны; волна 2 — после завершения 12. Strimzi и CloudNativePG
используются в волне 1 на уровне «оператор как сервис», без углубления в операторы.

**Вне охвата (возможная волна 3):** Quarkus, Dapr, Java Operator SDK, облако + OpenTofu, Spring AI, Backstage, chaos engineering.

## 5. Общие правила

### 5.1 Код
- Гексагональная структура сервиса: `domain` / `application` / `adapter.in` / `adapter.out`; с 02 проверяется ArchUnit.
- Spring Web MVC на virtual threads; WebFlux не используется.
- `--enable-preview` разрешён только в `01-modern-java`.
- Подпроекты не зависят друг от друга по коду; переиспользование — копированием. Общих библиотек нет.
- Конфигурация через env vars; Spring-профили только `local` и `test`.
- Логи — структурированный JSON в stdout.
- Комментарии и javadoc в коде, POM, YAML, скриптах — только на английском.

### 5.2 Тесты
- Разработка по TDD.
- Unit: JUnit 6, AssertJ, Mockito — surefire.
- Интеграционные (`*IT`): Testcontainers — failsafe. `mvn verify` не требует кластера и Compose.
- K8s smoke-тесты на kind — отдельный шаг CI начиная с 04.

### 5.3 CI
- Reusable workflow `build-subproject.yml`: checkout → JDK 25 (Temurin) → Maven cache → `mvn -B verify` → (опц.) kind smoke.
- Reusable workflow `publish-image.yml` (с 03): Jib → push в GHCR (`sha-<7>` + `latest`); вызывается после образных тестов, только на `main`.
- На каждый подпроект — свой workflow (копия `_template.yml`) с `paths`: каталог подпроекта, `pom.xml`, `.mvn/**`, `mvnw`, `mvnw.cmd`, `.gitattributes`, оба workflow-файла.
- Изменение корневого `pom.xml` запускает сборку всех подпроектов.

### 5.4 Документация (Documentation as Code)
- Корневой `docs/` и `docs/` каждого подпроекта; `docs/start.md` везде.
- Диаграммы — Mermaid (C4-стиль) в Markdown.
- Сквозные решения — ADR (MADR) в `docs/adr/`.
- Каждая задача/фикс/фича — impact-файл `impacts/impact_YYYY_MM_DD_HH.MM.SS.md` (≤ 1 экран; YAML `name`, `description`; далее смысл на RU и EN) в том же коммите.

### 5.5 Definition of Done подпроекта
1. `mvn -f NN-<name>/pom.xml verify` зелёный локально и в CI.
2. Есть `README.md`, `docs/start.md`, `docs/architecture.md`, `docs/learning-notes.md`.
3. Деплоящийся подпроект поднимается в общий kind (`k8s/` или Argo CD Application в `platform/`).
4. Сквозные решения оформлены ADR.

### 5.6 Процесс
Корневая спека (этот документ) → для каждого подпроекта: спека → план → реализация → ревью (Superpowers).

## 6. Домен «Мастерская Гефеста»

Описан в [docs/domain.md](../../domain.md).

## 7. Twelve-Factor → подпроекты

| Фактор | Где отрабатывается |
|---|---|
| I Codebase | Монорепо, один сервис = один deployable (все) |
| II Dependencies | Parent BOM, Buildpacks/Jib (02, 03) |
| III Config | Env vars, ConfigMap/Secret (04), External Secrets (21) |
| IV Backing services | Testcontainers/Compose (02+), платформа (05) |
| V Build, release, run | CI → GHCR → Kustomize/Helm → Argo CD (03, 05), Rollouts (20) |
| VI Processes | Stateless сервисы (все), Redis (08) |
| VII Port binding | Встроенный сервер, K8s Service (02, 04) |
| VIII Concurrency | Virtual threads (01), HPA/KEDA (12) |
| IX Disposability | Graceful shutdown, probes (04), быстрый старт (18) |
| X Dev/prod parity | kind + те же манифесты, Testcontainers (04, 05) |
| XI Logs | JSON в stdout → Loki (11) |
| XII Admin processes | Flyway-Job (04), CronJob (12) |
| +API first | OpenAPI (02), AsyncAPI/gRPC (14) |
| +Telemetry | OpenTelemetry (11) |
| +AuthN/AuthZ | Keycloak/OAuth2 (07) |

## 8. Среда

- Windows 11 + WSL2 (Ubuntu); Docker/Podman Desktop; kind; kubectl, helm, kustomize; JDK 25 (Temurin) + JDK 27 для 01; Maven 3.9+.
- Точный список и версии фиксируются в `docs/start.md` при реализации первого подпроекта.

## 9. Риски

| Риск | Мера |
|---|---|
| Ресурсы ноутбука для платформы (Kafka + Keycloak + LGTM + Postgres) | В `platform/` — профили «минимальный» и «полный»; лимиты ресурсов; выключаемые компоненты |
| Версии устаревают за время проекта | Renovate (22) — раньше: ручная проверка версий в начале каждого подпроекта |
| Объём 22 подпроектов | Строгий порядок, DoD, каждый подпроект — свой короткий цикл |
