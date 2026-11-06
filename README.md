# Hephaestus

[![_template](https://github.com/cucumber405d/hephaestus/actions/workflows/_template.yml/badge.svg)](https://github.com/cucumber405d/hephaestus/actions/workflows/_template.yml)
[![01-modern-java](https://github.com/cucumber405d/hephaestus/actions/workflows/01-modern-java.yml/badge.svg)](https://github.com/cucumber405d/hephaestus/actions/workflows/01-modern-java.yml)

Учебный монорепозиторий: микросервисы на современной Java (25 LTS, Spring Boot 4) в Kubernetes по принципам Twelve-Factor App.
Набор независимых подпроектов, пронумерованных как учебный маршрут; общий домен — «Мастерская Гефеста».

**EN.** Learning monorepo and reference architecture for cloud-native Java: microservices on Java 25 and
Spring Boot 4, running on Kubernetes, following Twelve-Factor App and DDD. 22 self-contained, numbered
subprojects cover event-driven architecture (Kafka, Saga, Outbox), CQRS/Event Sourcing, OAuth2/Keycloak,
GitOps (Argo CD), service mesh, observability (OpenTelemetry) and supply-chain security.
Shared domain: "Hephaestus' Workshop" (orders, inventory, forge, payment, delivery).

- Спецификация: [docs/superpowers/specs/2026-11-05-hephaestus-root-design.md](docs/superpowers/specs/2026-11-05-hephaestus-root-design.md)
- Домен: [docs/domain.md](docs/domain.md)
- Каталог технологий: [docs/technology-catalog.md](docs/technology-catalog.md)
- Решения: [docs/adr/](docs/adr/)
- Старт: [docs/start.md](docs/start.md)

## License

[Apache License 2.0](LICENSE)
