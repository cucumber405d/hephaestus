# _template — эталонный подпроект

Образец для копирования при создании нового подпроекта и проверка `hephaestus-parent` в CI.
Один сервис `greeting-service` в гексагональной структуре: `domain` → `application` → `adapter.in.web`.

## Новый подпроект из шаблона

1. Скопировать: `Copy-Item _template NN-<name> -Recurse` (без `target/`).
2. В `NN-<name>/pom.xml` — `artifactId`, `name`; в модуле — `artifactId`, `name`, родитель.
3. Переименовать пакет `de.ibikus.appa.hephaestus.template.greeting` → `de.ibikus.appa.hephaestus.<subproject>.<service>`.
4. Скопировать `.github/workflows/_template.yml` → `NN-<name>.yml`, заменить пути и `subproject`.

Документация: [docs/start.md](docs/start.md), [docs/architecture.md](docs/architecture.md), [docs/learning-notes.md](docs/learning-notes.md).
