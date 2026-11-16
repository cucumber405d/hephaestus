# 04-k8s-basics — Kubernetes руками

Сервис **Catalog** из образа 03 (`ghcr.io/cucumber405d/hephaestus/containers-ci-catalog`) разворачивается в
локальном кластере **kind** без Helm и операторов: Deployment и Service, ConfigMap и Secret, три probe на Actuator,
requests/limits с учётом поведения JVM, graceful shutdown без потерь запросов, миграция Flyway отдельным Job,
PostgreSQL как StatefulSet, Kustomize base + overlay.

**Чему учит:** объекты K8s и их связи, порядок деплоя, жизненный цикл пода, QoS и OOMKilled, неизменяемость Job,
разовые админ-процессы (12-factor XII), smoke-тест в kind на CI.

В подпроекте нет Java-кода: он деплоит артефакт 03 (зависимость по образу, не по коду).

- Спецификация: [docs/superpowers/specs/2026-11-16-04-k8s-basics-design.md](docs/superpowers/specs/2026-11-16-04-k8s-basics-design.md)
- План: [docs/superpowers/plans/2026-11-16-04-k8s-basics.md](docs/superpowers/plans/2026-11-16-04-k8s-basics.md)
- Старт: [docs/start.md](docs/start.md)
- Архитектура: [docs/architecture.md](docs/architecture.md)
- Выводы: [docs/learning-notes.md](docs/learning-notes.md)
