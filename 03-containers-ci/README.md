# 03-containers-ci — образы и CI

Облегчённый сервис **Catalog** (категории и изделия, PostgreSQL) упаковывается в OCI-образ четырьмя способами —
Buildpacks, Jib, Dockerfile со слоями Spring Boot, Dockerfile с собственным JRE (jlink) — и способы сравниваются
цифрами. Отдельно — как JVM видит лимиты контейнера, Compose со всем стеком и CI с публикацией образа в GHCR.

**Чему учит:** слои и кэш образов, воспроизводимые сборки, PID 1 и SIGTERM, non-root, container awareness JVM
(CPU, heap, выбор GC, OOMKill), jlink, Compose-профили, матрица в GitHub Actions, публикация в GHCR с минимальными
правами, теги против digest.

API: `GET /api/categories`, `GET /api/items?category=`, `GET /api/items/{id}`, `POST /api/items`,
`GET /actuator/runtime`.

- Спецификация: [docs/superpowers/specs/2026-11-13-03-containers-ci-design.md](docs/superpowers/specs/2026-11-13-03-containers-ci-design.md)
- План: [docs/superpowers/plans/2026-11-13-03-containers-ci.md](docs/superpowers/plans/2026-11-13-03-containers-ci.md)
- Старт: [docs/start.md](docs/start.md)
- Архитектура: [docs/architecture.md](docs/architecture.md)
- Выводы: [docs/learning-notes.md](docs/learning-notes.md)
