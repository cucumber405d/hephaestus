# Start

## Требования

- JDK 25 (Temurin): `winget install EclipseAdoptium.Temurin.25.JDK`; проверить `java --version`
- Docker (Desktop или Podman) — для Testcontainers в `*IT`-тестах
- Maven ставить не нужно — используется Maven Wrapper (`mvnw` / `mvnw.cmd`)

## Команды

```powershell
# Проверка корневого parent POM
.\mvnw.cmd -N validate

# Сборка и тесты одного подпроекта (без кластера)
.\mvnw.cmd -f <subproject>/pom.xml verify          # PowerShell
./mvnw -f <subproject>/pom.xml verify              # WSL / bash

# Локальные backing services подпроекта
docker compose -f <subproject>/compose.yaml up -d

# Проверить новые версии Boot и плагинов
.\mvnw.cmd -N versions:display-parent-updates versions:display-property-updates
```

Команды платформы (`platform/`) появятся в подпроекте 05.
