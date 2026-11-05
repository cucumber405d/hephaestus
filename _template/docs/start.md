# Start — _template

```powershell
# Из корня репозитория
.\mvnw.cmd -f _template/pom.xml spotless:apply   # отформатировать код
.\mvnw.cmd -f _template/pom.xml verify           # unit + IT + ArchUnit + spotless check

# Запуск сервиса
.\mvnw.cmd -f _template/greeting-service/pom.xml spring-boot:run
curl http://localhost:8080/api/greetings/Hephaestus
curl http://localhost:8080/actuator/health/readiness
```
