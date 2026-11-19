# 12-scaling-operations — design

## Зачем

Сага заказа из 11 переезжает в общий kind-кластер и проходит «пиковую распродажу»: резкий рост заказов,
автомасштабирование, выкатка и отказы подов посреди пика. Главное (решение Dmitry): **ни один принятый заказ не
должен потеряться.** Плюс итоговый аудит Twelve-Factor по всему репозиторию.

Решения записаны как ADR (формат и хранение — [ADR-0000](../../../../docs/adr/0000-use-madr.md)); спека их не
повторяет.

| Вопрос | ADR |
|---|---|
| Что значит «выдержали» | [0001](../../../../docs/adr/0001-peak-sale-success-criteria.md) |
| Чем масштабируем | [0002](../../../../docs/adr/0002-autoscaling-keda-kafka-lag.md) |
| Как не терять при масштабировании и обновлении | [0003](../../../../docs/adr/0003-no-loss-on-scaling-and-rollouts.md) |
| Чем даём нагрузку | [0004](../../../../docs/adr/0004-load-generation-gatling.md) |
| Какие помехи во время пика | [0005](../../../../docs/adr/0005-disruptions-during-peak.md) |
| Где и из чего разворачиваем | [0006](../../../../docs/adr/0006-deployment-on-platform.md) |
| Аудит 12 факторов | [0007](../../../../docs/adr/0007-twelve-factor-audit.md) |

## Цели / не-цели / анти-цели

- Цели: KEDA (Kafka-лаг + CPU) и metrics-server в платформе; сага 11 в `hephaestus-12` с PDB, стратегией
  обновления, мягкой остановкой; Gatling-распродажа; сценарии помех; сверка «ноль потерянных заказов»; дашборд
  масштабирования; аудит 12 факторов; CronJob очистки outbox.
- Не-цели: Argo Rollouts / canary (20), HPA по пользовательским метрикам через адаптер, мульти-брокерная Kafka,
  масштаб в ноль, нагрузка из кластера (distributed Gatling).
- Анти-цели: «зелёный» прогон, в котором заказы застряли в `PENDING`; настройки, которые прячут потери
  (например, `auto.offset.reset=latest` при отсутствии смещения).

## Состав

- Платформа: компоненты `keda`, `metrics-server` (профиль `full`), компонент `scaling` → `12-scaling-operations/k8s`.
- `12-scaling-operations/k8s/` — Kustomize (ADR-0006).
- `12-scaling-operations/load/` — Gatling (ADR-0004).
- `12-scaling-operations/scripts/peak.sh <scale|rollout|kill|drain>` — нагрузка + помеха + сверка (ADR-0001, 0005).
- `12-scaling-operations/grafana/scaling.json` — дашборд: реплики по сервисам, лаг групп, заказы в минуту,
  p95 приёма, `PENDING` сейчас; поставляется в lgtm платформы.
- Документация: README, `docs/{start,architecture,learning-notes,twelve-factor-audit}.md`; CI — workflow 12.

## Остаётся строителю

Версии KEDA/metrics-server под версию узлов kind; точные переменные окружения для ADR-0003 (или минимальная
правка 11); частоты нагрузки под ноутбук (в ADR-0001 фиксируются после первого прогона); как CI-раннер
вмещает платформу (возможно, урезанный профиль только для e2e 12).
