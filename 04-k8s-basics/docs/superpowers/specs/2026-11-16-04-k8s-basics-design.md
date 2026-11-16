# 04-k8s-basics — спецификация

- **Статус:** утверждена (Dmitry: «делай всё сразу», решения — на усмотрение исполнителя, перечислены в §2)
- **Дата:** 2026-11-16
- **Корневая спека:** [../../../../docs/superpowers/specs/2026-11-05-hephaestus-root-design.md](../../../../docs/superpowers/specs/2026-11-05-hephaestus-root-design.md)
- **Домен:** контекст Catalog; образ из 03 (`ghcr.io/cucumber405d/hephaestus/containers-ci-catalog`)

## 1. Цель

Развернуть Catalog в Kubernetes **руками**, без Helm и операторов: kind на Windows/WSL2, Deployment/Service,
ConfigMap/Secret, probes на Actuator, requests/limits с учётом выводов 03, graceful shutdown без потерь
запросов, Flyway как отдельный Job, Kustomize base + overlay. Проверка — smoke-тест на kind в CI.

**Критерии успеха**
- `scripts/up.sh` на чистой машине (Docker Desktop + kind + kubectl) поднимает кластер и сервис; `scripts/smoke.sh` зелёный.
- Smoke доказывает: API отвечает, миграция выполнена Job'ом (у приложения Flyway выключен), JVM в поде видит
  1 CPU и 768 MiB, `kubectl rollout restart` под нагрузкой проходит **без единого не-200**.
- `scripts/validate.sh` (kustomize build + kubeconform) проходит без кластера.
- CI: validate + smoke на kind на каждый PR.
- `docs/learning-notes.md` покрывает темы раздела 5.

**Не цели:** Helm, Argo CD, Gateway/Ingress, общая платформа (05); CloudNativePG (05); HPA, PDB (12);
NetworkPolicy, RBAC (07); External Secrets (21). **Анти-цели:** манифесты, которые «работают» только с
`kubectl apply` в нужном порядке руками; потери запросов при деплое.

## 2. Решения

| Тема | Решение | Отклонено |
|---|---|---|
| Код сервиса | **Нет Java-кода в 04**: используется образ 03 из GHCR (зависимость по артефакту, не по коду) | Копия Catalog в 04 — дублирование без новой темы |
| Сборка | Без `pom.xml`: подпроект — манифесты и скрипты; «одна команда без кластера» = `validate.sh` | Пустой Maven-модуль ради единообразия |
| Миграции | Job на **том же образе**: `SPRING_MAIN_WEB_APPLICATION_TYPE=none`, Flyway включён → мигрирует и выходит; в Deployment `SPRING_FLYWAY_ENABLED=false` (фактор XII) | Образ `flyway/flyway` + SQL в ConfigMap (дублирование миграций); Flyway при старте каждого пода (гонка реплик) |
| Порядок | `deploy.sh` применяет overlay по `app.kubernetes.io/component`: namespace/config/database → migration (ждёт `complete`) → api | Один `apply -k` и CrashLoop до миграции; initContainer-ожидание |
| Postgres | StatefulSet 1 реплика `postgres:17` + PVC (local-path kind) + headless Service | CloudNativePG (05) |
| Ресурсы | requests = limits по памяти `768Mi` (как в 03); CPU request `250m` (влезает в 2-vCPU CI-раннер), limit `1` | Без CPU limit (JVM видит все CPU ноды) — в заметки |
| Graceful | `preStop: sleep 10` + Spring graceful 20 s, `terminationGracePeriodSeconds: 45` | Без preStop — 502 в окне до удаления endpoint'а |
| Безопасность пода | `runAsNonRoot`, uid 1000, `readOnlyRootFilesystem` + emptyDir `/tmp`, drop ALL, seccomp RuntimeDefault | Дефолты |
| Секреты | `secretGenerator` в overlay `local` (литералы, только для kind) | External Secrets (21) |
| Образ | `images:` в overlay: тег `sha-d730159` (merge PR #3); локальный образ — через `kind load` | `latest` |

## 3. Структура

```
04-k8s-basics/
├── README.md
├── docs/ (start.md, architecture.md, learning-notes.md, superpowers/)
├── kind/cluster.yaml
├── k8s/
│   ├── base/        kustomization.yaml, postgres.yaml, catalog-config.properties, migration-job.yaml, catalog.yaml
│   └── overlays/local/  kustomization.yaml, namespace.yaml
└── scripts/ (up.sh, deploy.sh, smoke.sh, validate.sh, down.sh)
```
Namespace `hephaestus-04`; метки `app.kubernetes.io/{name,component,part-of}`.

## 4. Проверки

| Скрипт | Что |
|---|---|
| `validate.sh` | `kubectl kustomize overlays/local` → `kubeconform -strict` |
| `deploy.sh` | упорядоченный apply; ждёт StatefulSet, Job `complete`, rollout Deployment |
| `smoke.sh` | categories = 3; POST → 201, GET → 200; Job `succeeded=1`; `flyway_schema_history` есть, а у приложения Flyway выключен; `/actuator/runtime`: 1 CPU, лимит 805306368; rollout restart под нагрузкой из пода `curlimages/curl` — 0 не-200 |
| CI | job `validate`; job `smoke`: `helm/kind-action` → `deploy.sh` → `smoke.sh`; при падении — `kubectl get all,events` и логи |

## 5. Learning-notes

kind и WSL2; Deployment/ReplicaSet/Pod; Service и DNS; ConfigMap/Secret, хэш генератора и rollout; три probe и
чем они отличаются; requests/limits, QoS, CPU throttling, связь с `MaxRAMPercentage`; OOMKilled; жизненный цикл
пода при удалении (endpoints, preStop, SIGTERM, grace period); Job, неизменяемость `spec.template`, повторный
запуск; почему миграция не при старте; StatefulSet и PVC; securityContext; Kustomize base/overlay; опыты руками;
вопросы для собеседования.

## 6. Риски

| Риск | Мера |
|---|---|
| Пакет GHCR приватный | CI smoke не стянет образ → сделать пакет публичным (03/start.md) или `kind load` |
| Тег `sha-d730159` не опубликован | Проверить в GHCR; overlay меняется одной строкой |
| `preStop.sleep` требует K8s ≥ 1.30 | kind ставит свежий K8s; иначе `exec: sh -c "sleep 10"` (в Jib-образе есть shell) |
| Нагрузочный под и DNS в момент старта | smoke ждёт готовности пода нагрузки перед restart |
