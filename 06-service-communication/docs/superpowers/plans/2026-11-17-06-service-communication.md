# 06-service-communication Implementation Plan

**Goal:** Inventory (версии API, резерв с блокировками), Ordering (`@HttpExchange`-клиент с таймаутами и переводом ошибок), Spring Cloud Gateway (Web MVC); e2e в Compose; CI с публикацией; деплой на платформу за флагом.

**Spec:** `06-service-communication/docs/superpowers/specs/2026-11-17-06-service-communication-design.md`

**Execution:** inline, «всё сразу»; сборку и тесты запускает Dmitry/CI; одно независимое ревью в конце.

## Global Constraints
- Пакеты `de.ibikus.appa.hephaestus.servicecommunication.{inventory,ordering,gateway}`; reactor `service-communication`, модули → `service-communication-{inventory,ordering,gateway}`.
- Spring Boot 4.1.2 (из корня), Spring Cloud 2025.1.3 (BOM в reactor); JDK 25; Spotless palantir; комментарии — английский.
- Гексагональная структура в inventory и ordering + ArchUnit (правила 02/03).
- Problem Details, `type` = `https://hephaestus.ibikus.de/problems/<slug>`.
- Env: `PORT`, `SPRING_DATASOURCE_*`, `INVENTORY_URL`, `ORDERING_URL`; логи ECS JSON; graceful shutdown.
- Образы Jib: `hephaestus/service-communication-<module>:jib` локально, `ghcr.io/cucumber405d/hephaestus/service-communication-<module>` в CI.
- Impact-файл в каждом коммите.

## Tasks
1. Reactor, Jib-конфигурация, CI verify.
2. Inventory: домен, сервис, JPA, REST с версиями, тесты.
3. Ordering: домен, сервис, клиент Inventory, JPA, REST, тесты.
4. Gateway: маршруты, тест.
5. Compose + `scripts/e2e.sh`; CI e2e + публикация.
6. `k8s/` и Application на платформе (флаг выключен).
7. Документация.
8. Финальное ревью, исправления.
