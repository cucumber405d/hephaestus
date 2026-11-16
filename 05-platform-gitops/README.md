# 05-platform-gitops — общая платформа

Общий кластер [`platform/`](../platform/README.md), которым управляет **Argo CD по GitOps**: после одного
`helm install` всё остальное — включая сам Argo CD — Argo CD берёт из Git (App-of-Apps). На платформе — то, что
нужно подпроектам 06–12: **Gateway API** (Envoy Gateway), **CloudNativePG**, **Strimzi Kafka**, **Keycloak**,
**Redis**, наблюдаемость (**grafana/otel-lgtm**). Демонстрация — Catalog из 03 на CloudNativePG за Gateway, где
порядок «БД → миграция → API» задают sync waves Argo CD, а не скрипт.

**Чему учит:** GitOps (pull, drift, self-heal), Argo CD (App-of-Apps, multi-source, waves, хуки, health),
Helm против Kustomize, операторы и CRD, Gateway API против Ingress, профили платформы на ноутбуке и в CI.

- Спецификация: [docs/superpowers/specs/2026-11-16-05-platform-gitops-design.md](docs/superpowers/specs/2026-11-16-05-platform-gitops-design.md)
- План: [docs/superpowers/plans/2026-11-16-05-platform-gitops.md](docs/superpowers/plans/2026-11-16-05-platform-gitops.md)
- Старт: [docs/start.md](docs/start.md)
- Архитектура: [docs/architecture.md](docs/architecture.md)
- Выводы: [docs/learning-notes.md](docs/learning-notes.md)
