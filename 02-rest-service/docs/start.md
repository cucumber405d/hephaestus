# Start — 02-rest-service

Требования: JDK 25 (`JAVA_HOME`), запущенный Docker (Testcontainers, Compose).

```powershell
# Сборка и все тесты (поднимает PostgreSQL 17 в контейнере)
.\mvnw.cmd -f 02-rest-service/pom.xml spotless:apply verify

# Локальный запуск: Postgres из compose.yaml стартует автоматически (профиль local)
.\mvnw.cmd -f 02-rest-service/catalog/pom.xml spring-boot:run "-Dspring-boot.run.profiles=local"

# Проверить новые версии библиотек
.\mvnw.cmd -f 02-rest-service/pom.xml -N versions:display-property-updates
```
