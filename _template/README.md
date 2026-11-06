# _template — эталонный подпроект

Образец для копирования при создании нового подпроекта и проверка `hephaestus-parent` в CI.
Один сервис `greeting-service` в гексагональной структуре: `domain` ← `application` ← `adapter.in` / `adapter.out`.

## Соглашения об именах

| Что | Шаблон | Пример (`02-rest-service`, сервис `catalog`) |
|---|---|---|
| Каталог | `NN-<name>` | `02-rest-service` |
| artifactId reactor-POM | `<name>` | `rest-service` |
| Каталог и artifactId модуля | `<service>` / `<name>-<service>` | `catalog` / `rest-service-catalog` |
| Пакет | `de.ibikus.appa.hephaestus.<name без дефисов>.<service>` | `de.ibikus.appa.hephaestus.restservice.catalog` |
| Main-класс | `<Service>Application` | `CatalogApplication` |
| `spring.application.name` | `<service>` | `catalog` |
| Workflow | `.github/workflows/NN-<name>.yml` | `02-rest-service.yml` |

## Новый подпроект из шаблона — чек-лист

1. Скопировать без `target/`:
   - bash/WSL: `rsync -a --exclude target _template/ NN-<name>/`
   - PowerShell: `robocopy _template NN-<name> /E /XD target`
2. `NN-<name>/pom.xml`: `artifactId`, `name`, `description`, `<module>`.
3. Модуль: переименовать каталог `greeting-service`; в его `pom.xml` — `parent.artifactId`, `artifactId`, `name`.
4. Пакет `…template.greeting` → `…<name>.<service>` (main и test); `GreetingApplication` → `<Service>Application`.
5. `application.yaml`: `spring.application.name`.
6. `docs/*.md` и `README.md`: заголовки «— _template», содержание.
7. Workflow: `cp .github/workflows/_template.yml .github/workflows/NN-<name>.yml`; заменить `name:`, `_template/**` в обоих блоках `paths`, имя самого файла в `paths`, `subproject:`.
8. Бейдж нового workflow — в корневой `README.md`.
9. `.\mvnw.cmd -f NN-<name>/pom.xml spotless:apply verify` — зелёный.
10. Impact-файл `impacts/impact_YYYY_MM_DD_HH.MM.SS.md`, коммит.

Документация: [docs/start.md](docs/start.md), [docs/architecture.md](docs/architecture.md), [docs/learning-notes.md](docs/learning-notes.md).
