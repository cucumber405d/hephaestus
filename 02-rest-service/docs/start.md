# Start — 02-rest-service

Требования: JDK 25 (`JAVA_HOME`), запущенный Docker (Testcontainers, Compose).

```powershell
# Сборка и все тесты (поднимает PostgreSQL 17 в контейнере)
.\mvnw.cmd -f 02-rest-service/pom.xml spotless:apply verify

# Локальный запуск: Postgres из compose.yaml стартует автоматически (профиль local)
.\mvnw.cmd -f 02-rest-service/catalog/pom.xml spring-boot:run "-Dspring-boot.run.profiles=local"

# Swagger UI (рукописный контракт): http://localhost:8080/swagger-ui/index.html
# Готовые запросы: 02-rest-service/requests.http (IntelliJ HTTP Client)

# Проверить новые версии библиотек
.\mvnw.cmd -f 02-rest-service/pom.xml -N versions:display-property-updates
```
