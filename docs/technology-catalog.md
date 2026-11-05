# Hephaestus — каталог технологий (черновик для отбора)

> Статус: **отбор сделан 2026-11-05** — выбранный охват см. в [корневой спецификации](superpowers/specs/2026-11-05-hephaestus-root-design.md).

**Легенда приоритета**
- ★ **Ядро** — без этого «Microservices + K8s на Java» не освоены; всё это ждут на собеседованиях.
- ◆ **Углубление** — следующий уровень, отличает senior/архитектора.
- ○ **Обзор / альтернатива** — потрогать в одном подпроекте или сравнить, не тащить везде.

Версии — актуальные на ноябрь 2026, при старте каждого подпроекта перепроверить.

---

## 1. Язык и платформа Java

| Технология | Прио | Зачем / что освоить |
|---|---|---|
| **Java 25 LTS** (база) + **Java 27** (вышла 15.09.2026) для экспериментов | ★ | Records, sealed types, pattern matching (switch/record patterns), virtual threads, structured concurrency, scoped values, Stream Gatherers, FFM API, compact source files |
| Virtual Threads (Project Loom) | ★ | Блокирующий стиль вместо reactive, pinning, лимиты пулов соединений |
| Structured Concurrency / Scoped Values | ◆ | Fan-out вызовов к сервисам, отмена, контекст запроса вместо ThreadLocal |
| GraalVM Native Image | ◆ | Быстрый старт в K8s, ограничения reflection, AOT-hints |
| CDS / AOT cache (Project Leyden, JEP 483/514/515) | ◆ | Ускорение старта без native image |
| JFR + JDK Mission Control | ◆ | Профилирование в контейнере |
| Container awareness JVM (`-XX:MaxRAMPercentage`, cgroups v2) | ★ | Правильная память/CPU в поде |
| GC: G1 / ZGC (generational) | ◆ | Выбор GC под latency |

## 2. Фреймворки микросервисов

| Технология | Прио | Зачем |
|---|---|---|
| **Spring Boot 4.x / Spring Framework 7** (Jakarta EE 11, JSpecify null-safety) | ★ | Основной стек рынка DACH |
| Spring Web MVC (на virtual threads) | ★ | Синхронный REST |
| Spring WebFlux / Project Reactor | ○ | Понять reactive и когда он не нужен |
| Spring Data JPA / Hibernate 7 | ★ | Персистентность |
| Spring Data JDBC / jOOQ | ◆ | Альтернатива ORM, type-safe SQL |
| Spring Security 7 (OAuth2 Resource Server / Client) | ★ | JWT, scopes, method security |
| Spring Cloud Gateway | ★ | API Gateway |
| Spring Cloud Stream / Spring Kafka | ★ | Event-driven |
| Spring Modulith | ◆ | Модульный монолит → кандидаты в сервисы, события между модулями |
| Spring Cloud Config / Kubernetes ConfigMaps | ○ | Сравнить: в K8s конфиг обычно через ConfigMap/Secret |
| Spring Boot Actuator | ★ | Health/readiness/liveness, метрики |
| HTTP Interface clients (`@HttpExchange`, `RestClient`) | ★ | Декларативные клиенты вместо Feign |
| **Quarkus** | ○/◆ | Один подпроект для сравнения (Jakarta EE/MicroProfile-стиль, native-first) |
| Micronaut / Helidon | ○ | Только обзор |
| Jakarta EE 11 / MicroProfile 7 (Config, Health, Fault Tolerance, OpenAPI, Telemetry) | ○ | Мост к JEE-опыту, спецификации vs Spring |

## 3. Коммуникация между сервисами и контракты

| Технология | Прио | Зачем |
|---|---|---|
| REST + **OpenAPI 3.1** (contract-first, openapi-generator) или springdoc | ★ | Контракты API |
| RFC 9457 Problem Details | ★ | Единый формат ошибок |
| API-версионирование (Spring Framework 7 API versioning) | ◆ | Эволюция API |
| **gRPC + Protocol Buffers** (Spring gRPC) | ◆ | Синхронный бинарный внутренний трафик |
| GraphQL (Spring for GraphQL) | ○ | BFF/агрегация |
| **Apache Kafka** (KRaft, без ZooKeeper) | ★ | Event streaming, партиции, consumer groups, exactly-once |
| Schema Registry (Apicurio / Confluent) + Avro или Protobuf | ◆ | Эволюция схем событий |
| AsyncAPI | ◆ | Контракты для событий |
| RabbitMQ | ○ | Сравнение message broker vs log |
| CloudEvents | ○ | Стандартный конверт событий |
| Spring Cloud Contract / Pact | ◆ | Consumer-driven contract testing |

## 4. Паттерны микросервисной архитектуры

| Паттерн | Прио | Реализация |
|---|---|---|
| Database per Service | ★ | Отдельные схемы/БД |
| API Gateway, BFF | ★ | Spring Cloud Gateway |
| Service Discovery | ★ | K8s Services/DNS (вместо Eureka) |
| **Transactional Outbox** + CDC | ★ | Spring Modulith Events / Debezium |
| **Saga** (оркестрация и хореография) | ★ | Kafka + state machine; ◆ Temporal / Camunda 8 для оркестрации |
| Idempotent Consumer / Inbox | ★ | Дедупликация сообщений |
| CQRS, Event Sourcing | ◆ | Отдельная read-модель; ○ Axon Framework |
| Circuit Breaker, Retry, Bulkhead, Rate Limiter, Timeout | ★ | **Resilience4j**, Spring Retry |
| Strangler Fig, Anti-Corruption Layer | ◆ | Миграция «легаси-монолита» (очень релевантно для госсектора) |
| Distributed Locking / Leader Election | ◆ | ShedLock, K8s Lease |
| DDD: Bounded Contexts, Aggregates, Context Mapping, Event Storming | ★ | Нарезка сервисов |
| Hexagonal / Clean Architecture | ★ | Структура внутри сервиса; ArchUnit для проверки |

## 5. Данные и хранилища

| Технология | Прио | Зачем |
|---|---|---|
| **PostgreSQL 17/18** | ★ | Основная БД |
| Flyway или Liquibase | ★ | Миграции схемы (12-factor: admin processes) |
| Redis / Valkey | ★ | Кэш, rate limiting, distributed lock |
| MongoDB | ○ | Документная модель для одного сервиса |
| OpenSearch / Elasticsearch | ○ | Полнотекстовый поиск |
| Debezium | ◆ | CDC → Kafka |
| MinIO (S3-совместимое) | ○ | Object storage |
| CloudNativePG (оператор) | ◆ | PostgreSQL в K8s по-взрослому |
| HikariCP тюнинг | ★ | Пулы под virtual threads |

## 6. Безопасность

| Технология | Прио | Зачем |
|---|---|---|
| **Keycloak** | ★ | OIDC/OAuth2 IdP (стандарт в немецком госсекторе) |
| OAuth2/OIDC: Authorization Code + PKCE, Client Credentials, Token Exchange | ★ | Пользователь → сервис, сервис → сервис |
| JWT, JWKS, propagation токенов | ★ | |
| mTLS между сервисами | ◆ | Через service mesh или cert-manager |
| Secrets: K8s Secrets + **External Secrets Operator** / Sealed Secrets / **HashiCorp Vault / OpenBao** | ★/◆ | 12-factor config без секретов в Git |
| cert-manager | ◆ | TLS-сертификаты в кластере |
| Supply chain: **SBOM (CycloneDX)**, **Trivy**/Grype, **Cosign/Sigstore**, SLSA | ◆ | Подпись и сканирование образов (важно из-за EU Cyber Resilience Act) |
| OWASP Dependency-Check, OWASP ASVS / API Top 10 | ◆ | |
| Pod Security Standards, NetworkPolicies, RBAC | ★ | Безопасность в кластере |
| Kyverno / OPA Gatekeeper | ○ | Policy as Code |

## 7. Наблюдаемость (Observability)

| Технология | Прио | Зачем |
|---|---|---|
| **OpenTelemetry** (Java agent / Spring Boot OTel starter, OTel Collector) | ★ | Единый стандарт traces/metrics/logs |
| Micrometer (Observation API, Tracing) | ★ | Метрики и трассировка в Spring |
| **Prometheus** (+ kube-prometheus-stack, ServiceMonitor) | ★ | Метрики |
| **Grafana** | ★ | Дашборды |
| Loki | ★ | Логи (12-factor: logs as event streams → stdout) |
| Tempo или Jaeger | ★ | Распределённые трейсы |
| Structured logging (ECS/logfmt JSON, Spring Boot structured logging), MDC/trace-id | ★ | |
| Alertmanager, SLO/SLI, error budgets (Sloth/Pyrra) | ◆ | |
| Grafana Pyroscope | ○ | Continuous profiling |

## 8. Контейнеризация

| Технология | Прио | Зачем |
|---|---|---|
| Docker / Podman, multi-stage Dockerfile | ★ | |
| **Buildpacks** (`spring-boot:build-image`) и **Jib** | ★ | Образы без Dockerfile, layering |
| Distroless / Chainguard / Eclipse Temurin JRE базовые образы | ◆ | Минимальная поверхность атаки |
| Docker Compose | ★ | Локальная среда; Spring Boot Docker Compose support |
| OCI registry: Harbor / GHCR | ◆ | |

## 9. Kubernetes — основа

| Тема | Прио | Что освоить |
|---|---|---|
| Локальный кластер: **kind** / k3d / minikube (+ Rancher/Podman Desktop) | ★ | На Windows 11 + WSL2 |
| Pod, Deployment, ReplicaSet, Service, Ingress | ★ | |
| **Gateway API** (замена Ingress) + Envoy Gateway / Traefik / NGINX Gateway Fabric | ★ | Ingress-nginx уходит — Gateway API стандарт |
| ConfigMap, Secret, переменные окружения | ★ | 12-factor config |
| Liveness / Readiness / Startup probes ↔ Actuator | ★ | |
| Requests/Limits, QoS, JVM memory в контейнере | ★ | |
| HPA, VPA, **KEDA** (скейлинг по лагу Kafka) | ★/◆ | 12-factor concurrency |
| Rolling update, PodDisruptionBudget, graceful shutdown (preStop, SIGTERM) | ★ | 12-factor disposability |
| StatefulSet, PV/PVC, StorageClass | ◆ | |
| Job / CronJob | ★ | Миграции, batch (12-factor admin processes) |
| Namespaces, RBAC, ServiceAccount, NetworkPolicy | ★ | |
| Affinity, taints/tolerations, topology spread | ◆ | |
| CRD и Operators (Strimzi для Kafka, CloudNativePG) | ◆ | |
| Свой оператор на Java (**Java Operator SDK**, Fabric8 client) | ○ | Мощный финальный подпроект |
| Kubernetes-клиент из Java / Spring Cloud Kubernetes | ○ | |

## 10. Пакетирование и деплой в K8s

| Технология | Прио | Зачем |
|---|---|---|
| **Helm 3/4** (charts, values, library charts) | ★ | |
| **Kustomize** (base/overlays по окружениям) | ★ | 12-factor dev/prod parity |
| **Argo CD** (GitOps, App-of-Apps / ApplicationSet) | ★ | Декларативный деплой |
| Flux | ○ | Альтернатива Argo CD |
| **Argo Rollouts** / Flagger: canary, blue-green | ◆ | Progressive delivery |
| Skaffold / Tilt | ◆ | Inner loop разработки в кластере |
| Telepresence / mirrord | ○ | Отладка локально против кластера |

## 11. Service Mesh и сеть

| Технология | Прио | Зачем |
|---|---|---|
| **Istio (Ambient mode)** или Linkerd | ◆ | mTLS, traffic shifting, retries без кода |
| Cilium (CNI, eBPF, Hubble) | ○ | Сетевая наблюдаемость |
| Сравнение «resilience в коде (Resilience4j) vs в mesh» | ◆ | Архитектурный вопрос для собеседований |

## 12. CI/CD и качество

| Технология | Прио | Зачем |
|---|---|---|
| **Maven** (multi-module, BOM) или **Gradle** (Kotlin DSL, version catalogs) | ★ | Выбрать один как основной |
| **GitHub Actions** (или GitLab CI — популярен в DACH-госсекторе) | ★ | 12-factor build/release/run |
| Monorepo vs polyrepo, semantic versioning, Conventional Commits | ★ | |
| Renovate / Dependabot | ◆ | |
| SonarQube / SonarCloud, Spotless, Checkstyle/PMD, Error Prone + NullAway | ◆ | |
| JaCoCo, PIT (mutation testing) | ◆ | |

## 13. Тестирование

| Технология | Прио | Зачем |
|---|---|---|
| **JUnit 6**, AssertJ, Mockito | ★ | |
| **Testcontainers** (+ Spring Boot `@ServiceConnection`) | ★ | Интеграционные тесты с реальными Postgres/Kafka/Keycloak |
| Spring Boot test slices, MockMvc / RestTestClient | ★ | |
| WireMock | ★ | Стабы внешних сервисов |
| ArchUnit | ◆ | Архитектурные правила как тесты |
| Contract tests (Spring Cloud Contract / Pact) | ◆ | |
| Нагрузочное: **Gatling** (Java DSL) / k6 | ◆ | |
| Chaos engineering: Chaos Mesh / LitmusChaos, Toxiproxy | ○ | |
| E2E в кластере: kind в CI | ◆ | |

## 14. Документация и архитектура (Documentation as Code)

| Технология | Прио | Зачем |
|---|---|---|
| Markdown в `docs/`, **arc42**-структура | ★ | Стандарт в Германии |
| **ADR** (Architecture Decision Records, MADR) | ★ | Фиксация решений |
| **C4 model** + Structurizr DSL / PlantUML / Mermaid | ★ | Диаграммы как код |
| OpenAPI / AsyncAPI → сгенерированная документация | ◆ | |
| Backstage (developer portal, catalog-info.yaml) | ○ | Platform engineering |

## 15. Облако и платформа (опционально)

| Технология | Прио | Зачем |
|---|---|---|
| Managed K8s: AKS / EKS / GKE, **STACKIT / IONOS / Open Telekom Cloud** | ○ | Суверенное облако — тема для немецкого госсектора |
| Terraform / **OpenTofu** | ◆ | IaC |
| Crossplane | ○ | Облачные ресурсы через K8s API |
| Dapr | ○ | Sidecar-абстракции, сравнение со Spring |

## 16. AI-интеграция (мост к Appa, опционально)

| Технология | Прио | Зачем |
|---|---|---|
| Spring AI (ChatClient, RAG, pgvector, MCP server/client) | ○ | Один AI-микросервис в общей системе |
| LangChain4j | ○ | Альтернатива |

---

## 17. Twelve-Factor App → чем закрываем

| # | Фактор | Технологии в проекте |
|---|---|---|
| I | Codebase | Git, monorepo с подпроектами, один сервис = один deployable |
| II | Dependencies | Maven/Gradle BOM, Buildpacks/Jib, без системных зависимостей |
| III | Config | Env vars, ConfigMap/Secret, External Secrets, Spring profiles минимально |
| IV | Backing services | Postgres/Kafka/Redis как подключаемые ресурсы по URL; Testcontainers/Compose |
| V | Build, release, run | CI → подписанный образ (immutable tag) → Helm/Kustomize → Argo CD |
| VI | Processes | Stateless сервисы, состояние в Postgres/Redis |
| VII | Port binding | Встроенный Tomcat/Netty, K8s Service |
| VIII | Concurrency | HPA/KEDA, virtual threads, Kafka partitions |
| IX | Disposability | Graceful shutdown, быстрый старт (CDS/AOT/native), probes |
| X | Dev/prod parity | kind + те же Helm-чарты, Testcontainers, Kustomize overlays |
| XI | Logs | JSON в stdout → OTel Collector/Loki |
| XII | Admin processes | Flyway в Job/init-container, K8s Jobs/CronJobs |

**Beyond 12-factor** (Kevin Hoffman, «15 factors»): API first (OpenAPI/AsyncAPI), Telemetry (OTel), Authentication & Authorization (Keycloak/OAuth2).

---

## Открытые решения для брейншторма

1. Основной стек: Spring Boot only, или Spring + один Quarkus-подпроект для сравнения?
2. Maven или Gradle?
3. Сквозная доменная модель (одна бизнес-система, нарезанная на сервисы) или набор независимых учебных подпроектов?
4. GitHub Actions или GitLab CI?
5. Service mesh — входит в ядро или финальный этап?
6. Облако — только локальный kind, или финальный деплой в реальное облако?
