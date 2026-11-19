# Аудит Twelve-Factor (и «15 factors») — Hephaestus после 12

Статус: ✔ выполнено, ◐ частично, ✘ нарушено. Доказательство — файл или команда.

| Фактор | Статус | Доказательство | Где | Что не так |
|---|---|---|---|---|
| I Codebase | ✔ | монорепо, один сервис = один образ (`hephaestus/<sub>-<svc>`) | все | — |
| II Dependencies | ✔ | `pom.xml` (BOM Boot, Spring Cloud, Modulith), Jib без Dockerfile | 02, 03 | модуль `12/load` — без общего parent (сознательно, ADR-0004) |
| III Config | ◐ | только env: `application.yaml` с `${…}`, Deployment env в `12/k8s` | 04, 12 | секреты OAuth2 07 — в Git для обучения (`07-security/k8s/oauth2-secrets.yaml`); External Secrets — 21 |
| IV Backing services | ✔ | Kafka, PostgreSQL, lgtm — по адресу из env; Testcontainers/Compose/CNPG | 02+, 05 | — |
| V Build, release, run | ✔ | CI → GHCR `sha-<7>` → Kustomize `images` → Argo CD | 03, 05, 12 | откат — сменой тега в Git; Rollouts — 20 |
| VI Processes | ✔ | состояние только в БД/Kafka/Redis; реплики Ordering 2–6 без липких сессий | все, 12 | — |
| VII Port binding | ✔ | встроенный Tomcat на `PORT`, Service/HTTPRoute | 02, 04 | — |
| VIII Concurrency | ✔ | масштаб процессами: KEDA 1–6 реплик (`12/k8s/*ScaledObject`), виртуальные потоки внутри | 01, 12 | — |
| IX Disposability | ✔ | быстрый старт, мягкая остановка, `preStop`, PDB; прогон `peak.sh rollout/kill` | 04, 12 | старт JVM ~10–20 s; ускорение — 18 |
| X Dev/prod parity | ◐ | те же образы и манифесты в kind; Testcontainers с теми же версиями | 04, 05 | один брокер Kafka и инстанс PostgreSQL — не как в проде |
| XI Logs | ✔ | JSON в stdout (ECS) + OTLP в Loki | 11 | — |
| XII Admin processes | ✔ | Flyway при старте/Job (04, 06), Job `stock-refill`, CronJob `*-outbox-cleanup` | 04, 12 | — |
| API first | ◐ | OpenAPI (02) | 02 | контракты событий — 14 (AsyncAPI, Schema Registry) |
| Telemetry | ✔ | трейсы, метрики, логи — OpenTelemetry → lgtm | 11 | — |
| AuthN/AuthZ | ◐ | Keycloak, OAuth2, resource servers | 07 | сага 10–12 без аутентификации (учебное упрощение) |

Проверка командами:

```bash
kubectl -n hephaestus-12 get scaledobject,pdb,cronjob     # VIII, IX, XII
kubectl -n hephaestus-12 get deploy -o yaml | grep -A1 'name: SPRING_\|name: OTLP' | head   # III
12-scaling-operations/scripts/peak.sh rollout            # IX: выкатка под нагрузкой без потерь
```
