# Start — 03-containers-ci

Требования: JDK 25 (`JAVA_HOME`), запущенный Docker (Testcontainers, Compose, сборка образов).

```powershell
# Сборка и тесты без образов (поднимает PostgreSQL 17 в контейнере)
.\mvnw.cmd -f 03-containers-ci/pom.xml spotless:apply verify

# Локальный запуск: Postgres из compose.yaml стартует автоматически (профиль local)
.\mvnw.cmd -f 03-containers-ci/catalog/pom.xml spring-boot:run "-Dspring-boot.run.profiles=local"
```
