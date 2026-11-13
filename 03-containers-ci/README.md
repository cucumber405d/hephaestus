# 03-containers-ci — образы и CI

Облегчённый сервис **Catalog** (категории и изделия, PostgreSQL) упаковывается в OCI-образ четырьмя способами —
Buildpacks, Jib, Dockerfile со слоями Spring Boot, Dockerfile с собственным JRE (jlink) — и способы сравниваются
цифрами. Отдельно — как JVM видит лимиты контейнера, Compose со всем стеком и CI с публикацией образа в GHCR.

- Спецификация: [docs/superpowers/specs/2026-11-13-03-containers-ci-design.md](docs/superpowers/specs/2026-11-13-03-containers-ci-design.md)
- План: [docs/superpowers/plans/2026-11-13-03-containers-ci.md](docs/superpowers/plans/2026-11-13-03-containers-ci.md)
- Старт: [docs/start.md](docs/start.md)
- Выводы: [docs/learning-notes.md](docs/learning-notes.md)
