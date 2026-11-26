# Вопросы для учебника

Цель Hephaestus — разобрать проект по косточкам и собрать из него учебник по Java, Cloud, Kubernetes, GitHub Actions,
Argo CD и другим DevOps-технологиям. Здесь собираются вопросы, которые возникли по ходу работы (раздел «Заданные»,
с короткими ответами и ссылками на код), и вопросы, которые читатель скорее всего задаст (раздел «Ожидаемые») —
это план будущих глав. Имена сущностей — по-английски, с пояснением при первом упоминании.

## Заданные

### GitHub Actions

**Q: Перезапускаются ли после merge все jobs на GitHub?**
Нет. При `push` в `main` (merge — тоже push) запускаются только те workflow (файл в `.github/workflows/`, описывающий
конвейер), у которых фильтр `on.push.paths` совпал хотя бы с одним изменённым файлом. Изменение общего файла
(`publish-image.yml`, корневой `pom.xml`, `.mvn/**`, `mvnw`) запускает почти все workflow сразу — побочный эффект:
Maven Central отвечает `HTTP 429` (rate limit, слишком много запросов) части раннеров.
См. `.github/workflows/15-cdc-debezium.yml`, блок `on:`.

**Q: Где в workflow определены `paths`?**
В блоке `on:` — отдельно для `push` (с `branches: [main]`) и `pull_request`. `**` — любые файлы и подкаталоги.
`workflow_dispatch` — ручной запуск (`gh workflow run`) без фильтров.

**Q: Как связаны файл workflow и граф на странице run?**
Каждая плашка — job (задача на отдельном раннере, runner — виртуальная машина GitHub). Стрелки — поле `needs:`
(job ждёт другие). Рамка «Matrix» — `strategy.matrix`: один job размножается по значениям (например,
`mode: [debezium, modulith]` → 2 job). Jobs без `needs` стартуют сразу и параллельно. `if:` пропускает job
(например, `publish` только при push в main — поэтому в PR он «skipped»).

**Q: Как следить за проверками PR из терминала?**
`gh pr checks --watch` (ждёт, пока все checks завершатся); `--fail-fast` — выйти при первом падении.
Отдельный run: `gh run watch <id> --exit-status`; лог упавших шагов: `gh run view <id> --log-failed`.

**Q: Что означает «-» в статусе check (например, `08-resilience/publish (pull_request)`)?**
Skipped — job пропущен, не ошибка. У `publish` стоит `if: github.event_name == 'push' && github.ref ==
'refs/heads/main'`: в PR условие ложно. Значки `gh pr checks`: `✓` прошёл, `X` упал, `*` идёт или в очереди,
`-` пропущен (`if:` ложно или не выполнился job из `needs`).

**Q: Кто запустил workflow — человек или push?**
На странице run написано событие и автор: «Commit 2de88df pushed by …» — запуск по `push` (merge PR — тоже push в
main). Ручной запуск помечен `workflow_dispatch`, перезапуск — номером попытки (attempt). Изменение общего файла
из `paths` (например, `publish-image.yml`) запускает сразу почти все workflow.

**Q: Пропущенный («-») job ещё может выполниться?**
В этом run — нет: `if:` вычисляется один раз; `gh run rerun` в PR тоже не поможет (событие то же). `publish`
выполнится в новом run, который создаёт push в main после merge. Исключение: job пропущен из-за упавшего job в
`needs` — после `gh run rerun --failed` он может пойти.

**Q: Что такое `publish` в имени check?**
Имя job — ключ в блоке `jobs:` workflow подпроекта. Он вызывает reusable workflow (переиспользуемый workflow,
`on: workflow_call`) `publish-image.yml`: Jib (Maven-плагин, собирает образ без Dockerfile) собирает образ и
пушит в GHCR (GitHub Container Registry) с тегами `sha-<7>` и `latest`; эти образы потом разворачивает Argo CD.
Имя check: `<имя workflow>/<имя job> (<событие>)`.

**Q: «18» в PR — это порядковый номер?**
Да: issues и pull requests в репозитории нумеруются одной общей последовательностью.

**Q: `HTTP 429` от Maven Central — только из-за наших параллельных сборок? Есть ли limiter?**
Нет. Maven Central (Sonatype) ограничивает запросы по IP; GitHub-hosted runners делят пул IP со всеми
пользователями GitHub, так что лимит набирается и нашими параллельными workflow, и чужими сборками; в 2026 Sonatype
ужесточил лимиты для неаутентифицированного доступа. Свой limiter не поможет — лимит на стороне Central. Меры:
кэш `~/.m2` (`setup-java` `cache: maven`, уже есть), меньше одновременных запусков, повторы Maven Resolver на
429/503 с `Retry-After`, repository manager (Nexus, Artifactory, Reposilite) как прокси — рекомендация Sonatype.
См. https://central.sonatype.org/faq/429-error/

**Q: При 429 просто ждать? В какое время суток 429 меньше?**
Перезапускать упавшие jobs с паузой (`gh run rerun --failed <id>`, можно циклом). Публичной статистики 429 по часам
у Maven Central нет; оценка: больше всего сборок в будни ~15:00–23:00 CET (пересечение Европы и США), меньше —
ночью/утром CET и в выходные. Но наш 429 в основном самодельный: изменение общего файла запускает ~15 workflow
разом. Постоянное решение — повторы Maven Resolver на 429 в CI (отдельный PR в спокойное время: файл общий).

**Q: Как называется механизм, который отвечает 429?**
Rate limiter (ограничитель частоты запросов), механизм — rate limiting, синоним throttling. Ответ сверх лимита —
`429 Too Many Requests` (RFC 6585), часто с `Retry-After`. Алгоритмы: token bucket, leaky bucket, fixed / sliding
window. Стоит обычно в API gateway, балансировщике, CDN. Клиенту: retry with exponential backoff and jitter,
уважать `Retry-After`, client-side rate limiter (например, Resilience4j `RateLimiter`). Рядом: quota (лимит на
длинный период), load shedding (сброс нагрузки, обычно 503), backpressure (сигнал «медленнее» в потоках).

### Учения (drills)

**Q: Что такое drill?**
Учения (как fire drill — пожарные учения): плановая, контролируемая проверка отказоустойчивости. В 15 —
`scripts/drills.sh`: намеренно ломаем часть системы (пауза Kafka, `kill -9` сервиса или Kafka Connect, остановка
Connect) и проверяем, как каждый режим доставки переживает отказ, и что после восстановления у каждого принятого
заказа ровно один наряд (reconciliation — сверка).

**Q: Чем учения отличаются от e2e?**
e2e (end-to-end) проверяет, что весь поток работает в нормальных условиях. Учения проверяют поведение при сбоях.
e2e — предусловие для учений.

### Платформа

**Q: Что такое «платформа»?**
Общий Kubernetes-кластер для всех подпроектов (`platform/`), разворачиваемый по GitOps (состояние кластера описано
в Git). `platform/bootstrap.sh <профиль> [ревизия]` создаёт кластер kind (Kubernetes in Docker), ставит Argo CD и
корневое Application (ресурс Argo CD: «разверни этот путь Git в этот namespace»). Дальше Argo CD по схеме
App-of-Apps (одно Application порождает остальные) разворачивает компоненты (Envoy Gateway, CloudNativePG, Strimzi,
KEDA, …) и приложения подпроектов. Профили `minimal`, `scaling`, `full` — наборы флагов в `platform/apps/values-*.yaml`.

**Q: Что такое kind и почему платформа на нём? Это какой-то Unix?**
kind — Kubernetes IN Docker: инструмент, а не ОС. Каждый узел кластера — Docker-контейнер из образа
`kindest/node` (Linux с containerd, kubelet и, на control-plane, API-сервером); поды запускаются внутри этих
контейнеров. На Windows: Windows → WSL2 (Linux VM) → Docker → узлы kind → containerd → поды. Почему kind: локально и
бесплатно, тот же кластер в CI (`helm/kind-action`), несколько узлов (control-plane + 2 воркера → affinity и метки
имеют смысл), одноразовый (`kind create/delete cluster`), локальные образы — `kind load docker-image`.
Альтернативы: minikube, k3d (k3s в Docker), Kubernetes в Docker Desktop; в облаке — EKS, AKS, GKE.

### Терминал и Git Bash

**Q: Почему у скриптов расширение `.sh`?**
Традиция: `sh` — Bourne shell. Для запуска расширение не нужно: интерпретатор задаёт shebang (первая строка,
`#!/usr/bin/env bash` или `#!/bin/sh`), право на запуск — бит исполнения (`chmod +x`). `.sh` — для людей и
инструментов: подсветка, `shellcheck`, наша CI-проверка `*.sh|*.py`, ассоциация в Git Bash. У нас bash-скрипты
(`[[ ]]`, массивы, `pipefail`) работают на хосте и в CI; POSIX `sh` (`register-schemas.sh`,
`register-connector.sh`) — потому что выполняются и в контейнере `curlimages/curl`, где bash нет.

**Q: Почему после команды «ничего не происходит»?**
Вывод долгой команды (`gh run watch`) направлен в файл (`> file 2>&1`). Смотреть параллельно: `tail -f file`.

### Домен Hephaestus

**Q: Что такое Ordering?**
Сервис приёма заказов мастерской (Maven-модуль `ordering`, образ `hephaestus/cdc-debezium-ordering`, пришёл из 13/14).
`POST /api/orders`; stock order (например `HAMMER-01`) — резерв со своего склада (модуль `inventory`), сразу READY;
custom order (например `IRON-01`) — цена у Forge по gRPC (`QuoteService`), затем `WorkOrderRequest` (Avro) уходит
Forge: в режиме `debezium` через таблицу `ordering.outbox` и Debezium, в `modulith` — relay Spring Modulith.
`ForgeEvent` из `cdc.forge.events` переводит заказ в READY или REJECTED. Spring Boot 4 + Spring Modulith (модули
`ordering`, `inventory`), своя PostgreSQL, схемы — Flyway. Соседи: forge-service (цена и наряды), supplier-stub
(заглушка поставщика материалов).

## Ожидаемые

### Java и Spring
- Что такое Spring Modulith и чем «модуль» отличается от Maven-модуля?
- Как `@ApplicationModuleListener` и event publication registry (журнал публикаций событий) обеспечивают доставку?
- Почему `OutboxWriter` — синхронный `@EventListener`, а не `@TransactionalEventListener`?
- Как Flyway выбирает схему для `flyway_schema_history`, и при чём здесь `search_path` и `"$user"`?
- Зачем Testcontainers, и чем `@ServiceConnection` удобнее ручных свойств?

### Kafka, Avro, контракты
- Чем Avro + Schema Registry (Apicurio) лучше JSON в Kafka? Что такое совместимость BACKWARD?
- Как устроены байты сообщения Apicurio (schema id в payload vs headers)?
- Что такое idempotent producer, `max.block.ms`, `delivery.timeout.ms`, и почему timeout в `get()` не отменяет отправку?
- At-least-once vs exactly-once: где дубликаты, и как потребитель становится идемпотентным?
- Что проверяет Pact и чем consumer-driven contract отличается от schema registry?

### CDC и Debezium
- Что такое outbox pattern и проблема dual write?
- Как работает logical replication в PostgreSQL: publication, replication slot, `wal_level=logical`, pgoutput?
- Почему слот держит WAL, и что делает `max_slot_wal_keep_size`?
- Что такое Kafka Connect: worker, connector, task, converter, SMT (Single Message Transform)?
- Зачем Outbox Event Router, и почему heartbeat пришлось фильтровать?
- Почему после паузы Kafka коннектор ждал 5 минут (`scheduled.rebalance.max.delay.ms`, incremental cooperative rebalancing)?
- Почему задержка CDC (~0,5 с) выше, чем у relay (~15 мс)?

### Event sourcing и CQRS
- Чем event sourcing (состояние = журнал событий) отличается от event-driven architecture (события = сообщения)?
- Что такое stream (поток событий одного aggregate) и как из него получается состояние (`fold`, decide/evolve)?
- Как работает optimistic concurrency в журнале (`expectedVersion`, `unique(stream_id, version)`), и почему ответ — 409?
- Почему `bigserial` не годится как глобальный порядок для чтения, и что делает `pg_snapshot_xmin` (ADR-0018)?
- Что такое projection (проекция) и read model, что хранит checkpoint, и почему batch и checkpoint — одна транзакция?
- Как перестроить (rebuild/replay) read model, и почему publisher перестраивать нельзя?
- Что такое upcasting (подъём старой версии события при чтении), и чем он лучше миграции данных журнала?
- Что такое snapshot агрегата, и когда он нужен?
- Как ответить на вопрос «сколько было на складе в прошлый вторник» (time travel)?
- CQRS: почему запись не читает проекции, и что такое eventual consistency для пользователя?
- Как журнал служит outbox (ADR-0019), и зачем стабильный `eventId`?
- GDPR против неизменяемого журнала: crypto-shredding, ссылки на персональные данные.
- Когда event sourcing не нужен? Чем свой event store отличается от EventStoreDB (Kurrent) и Axon?

### Kubernetes и платформа
- Что такое operator и CRD (Custom Resource Definition) на примере Strimzi и CloudNativePG?
- Чем API Strimzi v1 отличается от v1beta2 для `KafkaConnect`?
- Что такое Gateway API (GatewayClass, Gateway, HTTPRoute) и чем он отличается от Ingress?
- Как Argo CD sync waves и hooks задают порядок развёртывания?
- Почему образы закрепляют на `sha-<7>`, а не на `latest`?
- Как Kustomize собирает манифесты (base/overlays, images, configMapGenerator, patches)?

### GitHub Actions и CI/CD
- Что такое reusable workflow (`workflow_call`) и как передаются inputs и permissions?
- Почему публикация образов запускается только на main, и как проверить её до merge?
- Как Jib собирает образ без Dockerfile, и почему ему нужны зависимости в `~/.m2`?
- Почему на Windows теряется бит исполнения скриптов (`core.fileMode`), и как это поймать в CI?
- Что делать с `HTTP 429` от Maven Central (кэш, повтор, зеркало)?
- Как настроить rate limiting на Envoy Gateway (BackendTrafficPolicy, local vs global)?

### Maven
- Что такое reactor, `-pl`, `-am`, `-N`, `relativePath`, и почему артефакт из `~/.m2` требует установленного parent POM?
- Зачем enforcer `dependencyConvergence`?

### Docker и окружение
- Почему контейнер убит с exit 137 / OOMKilled, и как heap JVM соотносится с лимитом контейнера?
- Как Git Bash переписывает пути (`MSYS_NO_PATHCONV`), и почему его нельзя включать глобально?
