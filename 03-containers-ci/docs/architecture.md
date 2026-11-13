# Architecture — 03-containers-ci

## C4: контейнеры

```mermaid
flowchart LR
    dev[Разработчик] -->|HTTP :8080| catalog
    subgraph runtime [Docker / Compose]
        catalog["catalog<br/>Spring Boot 4, Java 25<br/>образ hephaestus/catalog:*"] -->|JDBC| pg[(PostgreSQL 17)]
    end
    subgraph ci [GitHub Actions]
        verify[verify] --> images["images × 4<br/>build + image tests"] --> publish["publish<br/>Jib → GHCR"]
    end
    publish -->|sha-7, latest| ghcr[(ghcr.io/cucumber405d/<br/>hephaestus/containers-ci-catalog)]
```

Сервис — облегчённый Catalog: гексагональная структура (`domain` / `application` / `adapter.in.web`,
`adapter.in.actuator` / `adapter.out.persistence`), правила проверяет ArchUnit. Вся конфигурация — env vars.

## Слои образов

```mermaid
flowchart TB
    subgraph jib [Jib]
        j0[eclipse-temurin:25-jre] --> j1[dependencies] --> j2[snapshot-dependencies] --> j3[resources] --> j4[classes]
    end
    subgraph df [Dockerfile]
        d0[eclipse-temurin:25-jre] --> d1[dependencies] --> d2[spring-boot-loader] --> d3[snapshot-dependencies] --> d4[application]
    end
    subgraph jl [Dockerfile.jlink]
        l0[debian:stable-slim] --> l1[свой JRE из jlink] --> l2[dependencies] --> l3[spring-boot-loader] --> l4[snapshot-dependencies] --> l5[application]
    end
    subgraph bp [Buildpacks]
        b0[Paketo run image tiny] --> b1[JRE 25 + helpers] --> b2[dependencies] --> b3[spring-boot-loader] --> b4[snapshot-dependencies] --> b5[application]
    end
```

| | Buildpacks | Jib | Dockerfile | Dockerfile.jlink |
|---|---|---|---|---|
| Нужен Docker-демон | да | нет (`jib:build`) | да | да |
| Описание образа | конфигурация плагина | конфигурация плагина | Dockerfile | Dockerfile |
| Запуск | launcher Paketo → `java` | `java -cp … MainClass` | `JarLauncher` | `JarLauncher` |
| Флаги памяти | memory calculator | `MaxRAMPercentage=75` | `MaxRAMPercentage=75` | `MaxRAMPercentage=75` |
| Shell в образе | нет | да (Ubuntu) | да (Ubuntu) | да (Debian slim) |

## Ключевые решения

Подробно — в [спецификации](superpowers/specs/2026-11-13-03-containers-ci-design.md), раздел 2:
облегчённый сервис; все способы в одном модуле; Jib — канонический образ; образные тесты в профиле `image-it`;
свои ожидания лимитов для Buildpacks; публикация отдельным reusable-workflow после образных тестов.
