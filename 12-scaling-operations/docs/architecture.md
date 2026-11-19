# 12-scaling-operations — архитектура

```
 Gatling (хост) ──HTTP──▶ Gateway ─ orders.localtest.me ─▶ ordering (2..6, KEDA: cpu + лаг группы ordering)
                                                             │ outbox → Kafka (8 топиков × 6 партиций, Strimzi)
                                    payment / inventory / delivery (1..6, KEDA: лаг своей группы)
 KEDA ──читает лаг──▶ Kafka;  ──создаёт──▶ HPA keda-hpa-<svc> ──▶ Deployment.replicas
 metrics-server ──CPU──▶ HPA (триггер cpu у ordering)
 все сервисы ──OTLP──▶ lgtm.observability (метрики, трейсы, логи)        CNPG: база на сервис
```

## Что откуда

| Файл | Что | ADR |
|---|---|---|
| `platform/apps/templates/keda.yaml`, `metrics-server.yaml` | операторы масштабирования | 0002 |
| `platform/apps/values-scaling.yaml` | профиль только для 12 | 0006 |
| `k8s/topics.yaml` | 8 `KafkaTopic` × 6 партиций, волна 0 — раньше сервисов | 0002 |
| `k8s/<svc>.yaml` | CNPG, Deployment, Service, PDB, ScaledObject | 0002, 0003, 0006 |
| `k8s/route.yaml` | `orders.localtest.me` → ordering | 0006 |
| `k8s/outbox-cleanup.yaml` | CronJob на сервис: удалить отправленные публикации старше суток | 0007 |
| `k8s-jobs/stock-refill.yaml` | Job перед распродажей: пополнить склад | 0004, 0007 |
| `load/` | Gatling: модель, смесь заказов, журнал | 0004 |
| `scripts/peak.sh`, `reconcile.sh`, `lib.sh` | прогон, помехи, сверка, восстановление | 0001, 0005 |
| `grafana/scaling.json` | дашборд масштабирования | — |

## Почему заказ не теряется

1. **Outbox** (из 10): сообщение саги записано в той же транзакции, что и изменение; отправка после коммита и
   повтор, пока не уйдёт. Убитый под не теряет ни запись, ни сообщение.
2. **At-least-once + идемпотентность** (из 10): смещение коммитится после обработки записи; повтор после
   ребалансировки отсекается таблицей `processed_message`.
3. **Мягкая остановка**: `preStop` 5 s (под уходит из Endpoints) → Spring дорабатывает HTTP и текущую запись
   (до 20 s) → `terminationGracePeriodSeconds` 40.
4. **Протокол групп Kafka 4** (`group.protocol=consumer`): при входе и выходе пода переезжают только его партиции.
5. **Выкатка** `maxUnavailable: 0`: новый под готов раньше, чем остановлен старый. **PDB** `maxUnavailable: 1`:
   drain выселяет по одному.
6. **Таймаут шага 120 s**: под пиком сага ждёт очередь, а не отклоняет заказ.

Сверка (`reconcile.sh`) проверяет итог, а не механизмы: каждый принятый заказ в финальном статусе, исход как
задуман, резервы у подтверждённых есть, у отклонённых сняты.

## Масштаб

- Реплики консьюмеров ≤ партиций (6): KEDA не даёт больше, лишние простаивали бы.
- `replicas` в Deployment не задан: им владеет HPA, иначе Argo CD (self-heal) сбрасывал бы его.
- Вниз — после 120 s спокойствия (меньше ребалансировок), вверх — сразу.
- Ordering — минимум 2: вход не исчезает при drain.
