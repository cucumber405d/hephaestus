---
status: accepted
date: 2026-11-19
decision-makers: Dmitry
scope: 12-scaling-operations, platform
---

# ADR-0006: Разворачиваем сагу из 11 на общей платформе, без нового Java-кода

## Контекст и проблема

Для 12 нужен работающий распределённый процесс с Kafka и телеметрией. Сага из 11 есть, её образы опубликованы в
GHCR (`hephaestus/observability-*`), трейсы и метрики уже настроены. Платформа (05) даёт kind, Argo CD, Strimzi
Kafka, CNPG и lgtm в профиле `full`.

## Движущие факторы

- Тема 12 — эксплуатация, не код: минимум новых классов.
- Те же образы, что прошли e2e в 11 (Twelve-Factor V: build → release → run).
- Конфигурация — только переменные окружения (фактор III).

## Рассмотренные варианты

- A. **Образы 11 как есть + манифесты 12** (Kustomize), Application в App-of-Apps
- B. Копия саги в 12 с изменениями кода
- C. Отдельный кластер для 12

## Решение

Выбран **A**.

- `12-scaling-operations/k8s/` (Kustomize): namespace `hephaestus-12`; четыре Deployment из образов 11
  (`ghcr.io/cucumber405d/hephaestus/observability-*`, публичные пакеты — секрет не нужен, как в 06/07) с тегом
  по коммиту мержа 11; CNPG `Cluster` на сервис; `KafkaTopic` (6 партиций); ScaledObject (ADR-0002); PDB и стратегия
  обновления (ADR-0003); HTTPRoute `orders.localtest.me` на Ordering.
- Платформа: новый компонент `scaling` (Application) в `values-full.yaml`; компоненты `keda` и `metrics-server`.
- Настройки, которых нет в образах 11, — через переменные окружения Spring (`SPRING_KAFKA_*`,
  `SPRING_LIFECYCLE_*`, `OTLP_ENDPOINT=http://lgtm.observability:4318`).
- Если что-то из ADR-0003 нельзя задать окружением, это минимальное изменение кода в 11 отдельным коммитом
  (решает строитель, фиксирует в плане).

### Последствия

- Хорошо: 12 — чисто эксплуатационный подпроект; телеметрия 11 сразу показывает масштабирование в Grafana.
- Плохо: 12 зависит от образов 11; их изменение требует пересборки 11.
- Плохо: профиль `full` + нагрузка — тесно на ноутбуке (8–10 GB только платформа).

### Подтверждение

`kubectl -n hephaestus-12 get deploy` — четыре сервиса Ready; e2e 12 проходит в CI.
