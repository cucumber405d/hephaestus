# Start — 03-containers-ci

Требования: JDK 25 (`JAVA_HOME`), запущенный Docker (Testcontainers, Compose, сборка образов).
Скрипты — bash: на Windows запускать из Git Bash или WSL.

## Сборка и тесты

```powershell
# Сборка и тесты без образов (поднимает PostgreSQL 17 в контейнере)
.\mvnw.cmd -f 03-containers-ci/pom.xml spotless:apply verify

# Образные тесты против Jib-образа (профиль сам соберёт hephaestus/catalog:jib)
.\mvnw.cmd -f 03-containers-ci/pom.xml -Pimage-it verify
```

```bash
# Все четыре образа: hephaestus/catalog:{buildpacks,jib,dockerfile,dockerfile-jlink}
03-containers-ci/scripts/build-images.sh            # или: build-images.sh dockerfile jib

# Образные тесты против уже собранного образа другого способа
./mvnw -f 03-containers-ci/pom.xml -Pimage-it -Djib.skip=true -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false \
  -Dcatalog.image=hephaestus/catalog:buildpacks -Dcatalog.flavor=buildpacks verify

# Сравнение образов: Markdown-таблица в stdout
03-containers-ci/scripts/compare-images.sh > compare.md
```

## Запуск

```bash
# Из IDE / Maven: Postgres из compose.yaml стартует сам (профиль local)
./mvnw -f 03-containers-ci/catalog/pom.xml spring-boot:run -Dspring-boot.run.profiles=local

# Весь стек из образа (по умолчанию Jib; другой — CATALOG_IMAGE=hephaestus/catalog:dockerfile)
docker compose -f 03-containers-ci/compose.yaml --profile app up -d
curl localhost:8080/api/categories
curl localhost:8080/actuator/runtime        # что видит JVM под лимитом 768m / 1 CPU
docker compose -f 03-containers-ci/compose.yaml --profile app down

# Опыт с лимитами руками
docker run --rm --cpus 0.5 --memory 512m --entrypoint java hephaestus/catalog:dockerfile \
  -XX:+PrintFlagsFinal -version | grep -E 'MaxHeapSize|ActiveProcessorCount|UseSerialGC|UseG1GC'
```

Готовые запросы: `03-containers-ci/requests.http` (IntelliJ HTTP Client).

## CI и GHCR

- `.github/workflows/03-containers-ci.yml`: `verify` → `images` (четыре способа, образные тесты на каждом) →
  `publish` (только push в `main`) через reusable `publish-image.yml`.
- Образ: `ghcr.io/cucumber405d/hephaestus/containers-ci-catalog:sha-<7 символов>` и `:latest`; digest — в summary job.
- Первый push создаёт **приватный** пакет. Один раз сделать публичным: GitHub → профиль → Packages →
  `hephaestus/containers-ci-catalog` → Package settings → Change visibility → Public.
- Pull: `docker pull ghcr.io/cucumber405d/hephaestus/containers-ci-catalog:latest`.
