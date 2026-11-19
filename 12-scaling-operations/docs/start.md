# 12-scaling-operations — старт

## Кластер

```bash
platform/down.sh                                   # если поднят другой профиль
platform/bootstrap.sh scaling                      # kind + Argo CD + Gateway, CNPG, lgtm, Kafka, KEDA, metrics-server, сага
kubectl -n argocd get applications                 # ждём: все Synced / Healthy (первый раз 10-20 минут)
kubectl -n hephaestus-12 get deploy,scaledobject,pdb
```

Профиль `scaling` легче `full` (нет Keycloak, Redis, каталога). В `full` 12 тоже включено.

| Что | Где |
|---|---|
| Приём заказов | http://orders.localtest.me/api/orders |
| Grafana (admin/admin) | http://grafana.localtest.me — дашборды **Scaling (12)** (импортирует `peak.sh`) и **Saga** (из 11 — импортировать вручную из `11-observability/grafana/dashboards/saga.json`) |
| Argo CD | http://argocd.localtest.me |

## Распродажа

```bash
12-scaling-operations/scripts/peak.sh scale        # только нагрузка: смотрим автомасштабирование
12-scaling-operations/scripts/peak.sh rollout      # посреди пика — rollout restart всех четырёх сервисов
12-scaling-operations/scripts/peak.sh kill         # посреди пика — удаление подов (один — жёстко, как падение)
12-scaling-operations/scripts/peak.sh drain        # посреди пика — drain worker-узла без Kafka/БД/lgtm/Envoy
PEAK_RATE=40 PEAK_SECONDS=300 12-scaling-operations/scripts/peak.sh scale   # сильнее и дольше
```

`drain` нужен кластер, созданный с метками узлов `hephaestus.io/role` (stateful / drainable,
`platform/kind/cluster.yaml`): Kafka, базы, lgtm и Envoy предпочитают первый worker, второй можно осушить. На
кластере без меток `peak.sh drain` откажется до начала нагрузки — пересоздать: `platform/down.sh && platform/bootstrap.sh scaling`.

Каждый прогон: проверки перед стартом → пополнение склада (Job) → Gatling (прогрев 60 s, рост 30 s, пик, спад
60 s) с помехой посреди пика → сверка → восстановление → сводка. Файлы прогона —
`12-scaling-operations/load/target/peak-<сценарий>-<время>/` (журнал заказов, сэмплы реплик и лага, лог
Gatling); HTML-отчёт Gatling — `load/target/gatling/`.

Во время прогона полезно смотреть:

```bash
watch kubectl -n hephaestus-12 get hpa,pods          # KEDA создаёт HPA keda-hpa-<сервис>
kubectl -n kafka exec platform-dual-role-0 -c kafka -- /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 --describe --all-groups
```

## Руками

- **Потолок партиций:** при `PEAK_RATE=60` участники упираются в 6 реплик — больше партиций нет (ADR-0002).
- **Потолок памяти:** узлы kind сообщают всю память хоста, поэтому без ограничения планировщик разместил бы все
  поды, а ядро начало бы убивать процессы (вплоть до брокера Kafka). Квота namespace (`k8s/quota.yaml`, 10 GiB
  запросов) делает потолок видимым: лишний под не создаётся, у HPA — `FailedCreate`, сага ждёт в Kafka; сверка
  всё равно должна пройти. `kubectl -n hephaestus-12 describe resourcequota memory`.
- **Таймаут, который ловит падение, а не очередь:** `kubectl -n hephaestus-12 scale deploy delivery --replicas=0`
  (KEDA вернёт минимум, поэтому лучше `kubectl -n hephaestus-12 annotate scaledobject delivery
  autoscaling.keda.sh/paused-replicas=0`) — заказы оркестрации отклонятся через 120 s с `timeout at step delivery`.
  Это проверка 11, а для 12 такой отказ — провал прогона (ADR-0001).
- **CronJob очистки outbox:** `kubectl -n hephaestus-12 create job --from=cronjob/ordering-outbox-cleanup now`.
