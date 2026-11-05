# Root Scaffold Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Корневой каркас Hephaestus: `hephaestus-parent` POM + Maven Wrapper, эталонный подпроект `_template/`, на котором проверяется parent и с которого копируются новые подпроекты, и reusable CI на GitHub Actions.

**Architecture:** `hephaestus-parent` наследуется от `spring-boot-starter-parent` (версии и pluginManagement Boot — бесплатно, см. ADR 0001) и сам не агрегирует модули. Каждый подпроект — свой Maven-reactor с `<parent>` → `../pom.xml`. CI: один reusable workflow, вызываемый per-subproject workflow-файлами с `paths`-фильтром.

**Tech Stack:** Java 25 (Temurin), Maven 3.9 + Maven Wrapper, Spring Boot 4.1.x, JUnit 6/AssertJ/Mockito (через Boot), ArchUnit, Spotless (palantir-java-format), JaCoCo, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-11-05-hephaestus-root-design.md`

**Execution environment:** Maven Central недоступен из среды агента → `mvn`-команды выполняет Dmitry локально (PowerShell/WSL) или они проверяются CI после `git push`. Агент пишет файлы и коммитит; push — Dmitry.

## Global Constraints

- groupId `de.ibikus.appa.hephaestus`; пакеты `de.ibikus.appa.hephaestus.<subproject>.<service>` (для шаблона: `de.ibikus.appa.hephaestus.template.greeting`).
- Java `release 25`; `--enable-preview` запрещён везде, кроме `01-modern-java`.
- Spring Boot: последняя patch-версия линии **4.1** на момент реализации (зафиксирована 4.1.2). Версии плагинов, не управляемых Boot, — последний release с Maven Central, фиксируются в `<properties>` корневого POM.
- Корневой POM — только parent (`packaging pom`, без `<modules>`).
- Сборка подпроекта: `./mvnw -f <subproject>/pom.xml verify` — без кластера и без Compose.
- Unit — surefire (`*Test`), интеграционные — failsafe (`*IT`).
- Гексагональные пакеты сервиса: `domain`, `application`, `adapter.in`, `adapter.out`; проверка ArchUnit.
- Spring Web MVC + `spring.threads.virtual.enabled=true`; логи — структурированный JSON в stdout.
- Spring-профили только `local` и `test`; конфигурация через env vars.
- Документация в `docs/`, `docs/start.md` в корне и в каждом подпроекте.
- Коммиты заканчиваются строками атрибуции из сессии.

## Review Focus

1. **Сборка подпроекта из его каталога** (`cd _template && ../mvnw verify`) так же, как из корня через `-f` — parent должен разрешаться по `relativePath` без `mvn install` корня.
2. **Не та JDK локально** (например, 21 в PATH) → сборка должна падать сразу с понятным сообщением enforcer'а, а не ошибкой компилятора.
3. **Windows-переводы строк**: `mvnw` с CRLF не запускается в WSL/CI, Spotless ругается на CRLF → `.gitattributes` и `lineEndings` в Spotless.
4. **Изменение корневого `pom.xml`** должно запускать CI всех подпроектов, изменение в `_template/` — только его.
5. **Нарушение гексагональной архитектуры** (`domain` импортирует Spring или `adapter`) должно ронять `verify`, а не проходить молча.

---

### Task 1: Корневой parent POM, Maven Wrapper, ADR, домен

**Files:**
- Create: `pom.xml`
- Create: `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties`
- Create: `docs/adr/0000-template.md`, `docs/adr/0001-parent-inherits-spring-boot-starter-parent.md`
- Create: `docs/domain.md`
- Modify: `docs/start.md`, `README.md`, `.gitattributes`, спека §3.1 (одна строка: «наследуется от spring-boot-starter-parent, см. ADR 0001» вместо «import Spring Boot BOM»)

**Interfaces:**
- Produces: `de.ibikus.appa.hephaestus:hephaestus-parent:0.1.0-SNAPSHOT` (packaging `pom`); properties `java.version=25`, `archunit.version`, `jib-maven-plugin.version`, `spotless-maven-plugin.version`; profile-независимые плагины в `<build><plugins>`: enforcer, spotless, jacoco, failsafe (все наследуются подпроектами автоматически).

- [ ] **Step 1: Проверить окружение**
Run: `java -version` и `mvn -v`
Expected: Java 25 (Temurin), Maven ≥ 3.9. Если нет — `winget install EclipseAdoptium.Temurin.25.JDK` / Maven через SDKMAN в WSL; зафиксировать в `docs/start.md` раздел «Требования».

- [ ] **Step 2: Написать `pom.xml`**
- `<parent>`: `org.springframework.boot:spring-boot-starter-parent:<4.1.x>` с `<relativePath/>`.
- `dependencyManagement`: `com.tngtech.archunit:archunit-junit5:${archunit.version}` (scope test).
- `pluginManagement`: `jib-maven-plugin`, `spotless-maven-plugin` с версиями из properties.
- `build/plugins` (активны везде):
  - `maven-enforcer-plugin`: `requireJavaVersion [25,)`, `requireMavenVersion [3.9,)`, `dependencyConvergence`, `banDuplicatePomDependencyVersions`.
  - `spotless-maven-plugin`: `palantirJavaFormat`, `removeUnusedImports`, `lineEndings UNIX`, goal `check` в фазе `verify`.
  - `jacoco-maven-plugin`: `prepare-agent` + `report` (без порога покрытия).
  - `maven-failsafe-plugin`: goals `integration-test`, `verify`.
- `<description>`, `<url>https://github.com/cucumber405d/hephaestus</url>`, `<scm>`.

- [ ] **Step 3: Проверить POM**
Run: `mvn -N validate` и `mvn -N help:effective-pom -Doutput=target/effective-pom.xml`
Expected: `BUILD SUCCESS`; в effective-pom `<maven.compiler.release>25</maven.compiler.release>` (из `java.version`).

- [ ] **Step 4: Сгенерировать Maven Wrapper**
Run: `mvn -N wrapper:wrapper -Dmaven=<последний 3.9.x>`; затем `./mvnw -v` (WSL) и `.\mvnw.cmd -v` (PowerShell)
Expected: обе команды печатают зафиксированную версию Maven. В `.gitattributes` добавить `mvnw text eol=lf` (уже покрыто `* text=auto eol=lf`, проверить `git ls-files --eol mvnw` → `i/lf`).

- [ ] **Step 5: ADR и домен**
- `0000-template.md` — шаблон MADR (Context, Decision Drivers, Considered Options, Decision Outcome, Consequences).
- `0001-…` — решение: parent наследует `spring-boot-starter-parent`, а не импортирует BOM. Причина: Boot уже настраивает compiler/surefire/failsafe/resources/buildpacks; минус: один parent-слот занят Boot (не критично — подпроекты наследуют от нашего parent).
- `docs/domain.md` — перенести §6 спеки дословно; в спеке §6 оставить ссылку.
- `docs/start.md` — «Требования» (Java 25, Docker для Testcontainers) и команда `./mvnw -f <subproject>/pom.xml verify`.

- [ ] **Step 6: Commit**
```bash
git add pom.xml mvnw mvnw.cmd .mvn docs README.md .gitattributes
git commit -m "build: hephaestus-parent POM, Maven Wrapper, ADR 0001, domain model"
```

---

### Task 2: Эталонный подпроект `_template/`

**Files:**
- Create: `_template/pom.xml` (reactor, parent `../pom.xml`, `<modules><module>greeting-service</module></modules>`)
- Create: `_template/greeting-service/pom.xml` (starters: `web`, `actuator`, `validation`; test: `spring-boot-starter-test`, `spring-boot-starter-webmvc-test`, `archunit-junit5`; plugin `spring-boot-maven-plugin`)
- Create: `_template/greeting-service/src/main/java/de/ibikus/appa/hephaestus/template/greeting/`
  - `GreetingApplication.java`
  - `domain/Greeting.java`
  - `application/GreetingService.java`
  - `adapter/in/web/GreetingController.java`, `adapter/in/web/GreetingResponse.java`
- Create: `_template/greeting-service/src/main/resources/application.yaml`
- Test: `_template/greeting-service/src/test/java/de/ibikus/appa/hephaestus/template/greeting/`
  - `domain/GreetingTest.java`, `application/GreetingServiceTest.java`
  - `adapter/in/web/GreetingControllerTest.java`
  - `HexagonalArchitectureTest.java`, `GreetingApiIT.java`
- Create: `_template/README.md`, `_template/docs/start.md`, `_template/docs/architecture.md`, `_template/docs/learning-notes.md`

**Interfaces:**
- Consumes: `hephaestus-parent` (Task 1).
- Produces (копируемый образец для подпроектов):
  - `record Greeting(String recipient)` — `message()` → `"Hello, " + recipient + "!"`; конструктор бросает `IllegalArgumentException` при `null`/blank или длине > 50.
  - `class GreetingService { Greeting greet(String recipient) }`
  - `GET /api/greetings/{recipient}` → `200 {"message":"Hello, Hephaestus!"}`; невалидный `recipient` → `400` `application/problem+json` (RFC 9457) с `title` = `"Invalid recipient"`.
  - Actuator: `/actuator/health/liveness`, `/actuator/health/readiness` → `UP`.

- [ ] **Step 1: Написать падающие тесты**
```java
// GreetingTest
assertThat(new Greeting("Hephaestus").message()).isEqualTo("Hello, Hephaestus!");
assertThatThrownBy(() -> new Greeting(" ")).isInstanceOf(IllegalArgumentException.class);
assertThatThrownBy(() -> new Greeting("x".repeat(51))).isInstanceOf(IllegalArgumentException.class);

// GreetingControllerTest (@WebMvcTest)
mvc.get().uri("/api/greetings/Hephaestus") → status 200, $.message == "Hello, Hephaestus!"
mvc.get().uri("/api/greetings/" + "x".repeat(51)) → status 400,
    contentType application/problem+json, $.title == "Invalid recipient"

// HexagonalArchitectureTest (@AnalyzeClasses(packages = "de.ibikus.appa.hephaestus.template.greeting"))
layeredArchitecture(): domain ← application ← adapter; domain не зависит от org.springframework..
noClasses().that().resideInAPackage("..domain..").should().dependOnClassesThat().resideInAPackage("..adapter..")

// GreetingApiIT (@SpringBootTest(webEnvironment = RANDOM_PORT), RestTestClient)
GET /api/greetings/Hephaestus → 200
GET /actuator/health/readiness → 200, $.status == "UP"
```

- [ ] **Step 2: Убедиться, что тесты падают**
Run: `./mvnw -f _template/pom.xml verify`
Expected: FAIL — ошибки компиляции (классов нет).

- [ ] **Step 3: Реализовать классы из Interfaces**
`GreetingController` ловит `IllegalArgumentException` через `@ExceptionHandler` → `ProblemDetail.forStatusAndDetail(400, ex.getMessage())` с `setTitle("Invalid recipient")`. `application.yaml`: `spring.application.name=greeting-service`, `spring.threads.virtual.enabled=true`, `spring.mvc.problemdetails.enabled=true`, `logging.structured.format.console=ecs`, `management.endpoint.health.probes.enabled=true`, `server.shutdown=graceful`, `server.port=${PORT:8080}`.

- [ ] **Step 4: Тесты зелёные**
Run: `./mvnw -f _template/pom.xml verify`
Expected: `BUILD SUCCESS`; surefire: 3 класса тестов, failsafe: `GreetingApiIT` — 2 теста; spotless check проходит; `target/site/jacoco/index.html` создан.

- [ ] **Step 5: Проверки из Review Focus**
- `cd _template; ..\mvnw.cmd verify` → `BUILD SUCCESS` (RF1).
- Временно добавить в `Greeting` импорт `org.springframework.util.Assert` и использовать → `verify` FAIL в `HexagonalArchitectureTest` (RF5); откатить.
- Запустить с JDK 21 (`JAVA_HOME` на 21) → FAIL на enforcer с `requireJavaVersion` (RF2), если JDK 21 есть; иначе пропустить и отметить.
- `./mvnw -f _template/pom.xml spring-boot:run` → `curl localhost:8080/api/greetings/Hephaestus` возвращает JSON; лог в stdout — ECS JSON.

- [ ] **Step 6: Документация шаблона**
`README.md`: «эталон для копирования: `cp -r _template NN-<name>`, переименовать artifactId/пакеты». `docs/start.md`: команды verify и spring-boot:run. `docs/architecture.md`: Mermaid-диаграмма пакетов (adapter.in.web → application → domain). `docs/learning-notes.md`: заголовки «Выводы», «Подводные камни», «Вопросы для собеседования».

- [ ] **Step 7: Commit**
```bash
git add _template
git commit -m "feat(template): reference subproject with hexagonal greeting-service"
```

---

### Task 3: CI — reusable workflow

**Files:**
- Create: `.github/workflows/build-subproject.yml`
- Create: `.github/workflows/_template.yml`
- Modify: `README.md` (badge `_template` workflow)

**Interfaces:**
- Consumes: `./mvnw`, `_template/pom.xml`.
- Produces: reusable workflow `build-subproject.yml`, `on: workflow_call`, input `subproject: string` (обяз.); job `build` на `ubuntu-latest`: `actions/checkout` → `actions/setup-java` (`distribution: temurin`, `java-version: 25`, `cache: maven`) → `./mvnw -B -ntp -f ${{ inputs.subproject }}/pom.xml verify` → `actions/upload-artifact` отчётов surefire/failsafe/jacoco при любом исходе (`if: always()`). `permissions: contents: read`. Image-push и kind-smoke — не в этом плане (появятся в 03/04).
- Caller-шаблон для каждого подпроекта (`_template.yml`):
  `on: push/pull_request` с `paths: ['_template/**', 'pom.xml', '.mvn/**', '.github/workflows/build-subproject.yml', '.github/workflows/_template.yml']`; `uses: ./.github/workflows/build-subproject.yml`, `with: subproject: _template`; `concurrency: group: ${{ github.workflow }}-${{ github.ref }}, cancel-in-progress: true`.

- [ ] **Step 1: Написать оба workflow**

- [ ] **Step 2: Линт**
Run: `actionlint` (если установлен; `winget install rhysd.actionlint`)
Expected: без ошибок.

- [ ] **Step 3: Commit и push**
```bash
git add .github README.md
git commit -m "ci: reusable build-subproject workflow and _template pipeline"
git push
```

- [ ] **Step 4: Проверить запуск**
Run: `gh run watch` → затем `gh run list --workflow _template.yml --limit 1`
Expected: `completed success`; артефакт с отчётами есть.

- [ ] **Step 5: Проверить path-фильтр (RF4)**
Изменить только `docs/start.md`, push → `gh run list --limit 1` не показывает новый запуск `_template`. Изменить комментарий в корневом `pom.xml`, push → запуск `_template` есть.
