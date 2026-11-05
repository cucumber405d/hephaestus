# Start

```bash
# Сборка и тесты одного подпроекта (без кластера)
mvn -f NN-<name>/pom.xml verify

# Локальные backing services подпроекта
docker compose -f NN-<name>/compose.yaml up -d
```

Команды платформы (`platform/`) появятся в подпроекте 05.
