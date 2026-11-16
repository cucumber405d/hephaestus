# Learning notes — 04-k8s-basics

## kind на Windows

- kind запускает узлы Kubernetes как Docker-контейнеры; на Windows — внутри VM WSL2 Docker Desktop. Образы из
  локального Docker в кластере не видны: либо реестр (GHCR), либо `kind load docker-image`.
- Один узел достаточно для 04; многоузловой общий кластер — `platform/` в 05.

## Объекты и связи

- **Deployment → ReplicaSet → Pod.** Deployment хранит желаемое состояние и стратегию, каждая версия шаблона пода —
  свой ReplicaSet (по ним работает `kubectl rollout undo`).
- **Service** выбирает поды по меткам и даёт стабильное DNS-имя (`catalog`, `catalog.hephaestus-04.svc`).
  Селектор должен совпадать с метками шаблона пода — главный источник «Service без endpoint'ов».
  `postgres` — headless (`clusterIP: None`): DNS указывает прямо на под, этого требует StatefulSet.
- **ConfigMap и Secret** приходят в контейнер как env (12-factor III) — тот же образ, что в Compose и CI.
  Secret — это base64, а не шифрование: защищает только RBAC и шифрование etcd; секреты в Git — только для kind.
- **Хэш генератора:** Kustomize дописывает к имени ConfigMap/Secret хэш содержимого и подставляет его во все ссылки.
  Изменили значение → изменился шаблон пода → rollout. Без этого поды продолжают работать со старыми env до рестарта.

## Probes

| Probe | Вопрос | Ошибка → | Здесь |
|---|---|---|---|
| startup | «Уже стартовал?» — пока не прошла, остальные не работают | рестарт после `failureThreshold` | `/actuator/health/liveness`, до 60 s |
| liveness | «Жив ли процесс?» | **рестарт** контейнера | `/actuator/health/liveness` — без БД |
| readiness | «Можно слать трафик?» | под убирается из Service | `/actuator/health/readiness` + `db` |

- База **не** входит в liveness: при падении БД liveness перезапускал бы все поды разом — каскад без пользы.
  В readiness — входит: под без БД не получает трафик (спорно: если БД общая, отключатся все поды; альтернатива —
  отдавать 503 из приложения).
- Startup probe заменяет большой `initialDelaySeconds` у liveness: медленный старт не приводит к рестарту, а
  после старта liveness реагирует быстро.

## Ресурсы и JVM

- `requests` — сколько резервирует планировщик; `limits` — потолок. Память `request = limit` (768Mi): под не
  выселяется за превышение запроса, а JVM получает предсказуемый heap (75 % через `MaxRAMPercentage` из образа 03).
- QoS: `Guaranteed` — только если requests = limits для **всех** ресурсов всех контейнеров; здесь CPU 500m/1 →
  `Burstable`. Порядок выселения при нехватке памяти на ноде: BestEffort → Burstable → Guaranteed.
- **CPU limit** — это CFS-квота: при превышении процесс **троттлится** (не убивается). JVM по лимиту считает
  `availableProcessors` (здесь 1 → SerialGC, один поток GC, маленький common pool). Без CPU limit JVM увидит все CPU
  ноды и раздует пулы. Многие команды ставят только request — тогда задают `-XX:ActiveProcessorCount` явно.
- **OOMKilled:** превышение memory limit — SIGKILL от ядра, `Last State: Terminated, Reason: OOMKilled, Exit Code: 137`,
  без логов JVM. Heap — не вся память процесса (metaspace, стеки, code cache, direct buffers) — отсюда запас 25 %.

## Graceful shutdown

1. Под переходит в Terminating; удаление из EndpointSlice и обновление kube-proxy на всех нодах идут **асинхронно**.
2. Kubelet запускает `preStop` (здесь `sleep 10` — нативное действие, K8s ≥ 1.30, shell в образе не нужен):
   под ещё принимает запросы, пока все узнают, что его больше нет.
3. SIGTERM → Spring перестаёт принимать новые, дорабатывает текущие (до 20 s).
4. После `terminationGracePeriodSeconds` (45 s, отсчёт с начала, preStop входит) — SIGKILL.

Без `preStop` запросы, уже направленные на под, попадают в закрытый порт → ошибки при каждом деплое. Smoke-тест
доказывает обратное: `rollout restart` под нагрузкой — 0 неуспешных запросов. `maxUnavailable: 0`, `maxSurge: 1` —
новый под должен стать ready раньше, чем уйдёт старый.

## Миграция как Job

- Flyway при старте каждого пода — гонка реплик (Flyway держит lock, но медленная миграция → startup probe →
  рестарты) и смешение ролей. Job на **том же образе** с `SPRING_MAIN_WEB_APPLICATION_TYPE=none`: контекст
  поднимается, Flyway мигрирует, `main` завершается, JVM выходит с 0. У API `SPRING_FLYWAY_ENABLED=false`,
  `ddl-auto: validate` проверит, что схема совпадает.
- В Kubernetes нет порядка между объектами: без `deploy.sh` API падал бы в CrashLoopBackOff, пока Job не закончит.
  Альтернативы: init-контейнер, ждущий схему; Argo CD sync waves/hooks (05); Helm hooks.
- `spec.template` Job **неизменяем**, завершённый Job не перезапускается: `deploy.sh` удаляет и создаёт его заново.
  `backoffLimit: 3`, `activeDeadlineSeconds: 300` — предел попыток и времени.

## StatefulSet

Стабильные имена (`postgres-0`), свой PVC на реплику из `volumeClaimTemplates`, упорядоченный старт. PVC
переживает удаление пода и даже StatefulSet; данные пропадают только с кластером (`down.sh`). Для настоящего
PostgreSQL (реплики, бэкапы, failover) — оператор CloudNativePG в 05.

## securityContext

`runAsNonRoot` + uid 1000 (образ 03 и так не root — K8s проверяет), `readOnlyRootFilesystem` с `emptyDir` для
`/tmp` (Tomcat, hsperfdata), `allowPrivilegeEscalation: false`, `capabilities.drop: [ALL]`, seccomp `RuntimeDefault`.
Это требования Pod Security Standard «restricted» для API и Job.

## Kustomize

`base` — общее, `overlays/local` — namespace, образ, секрет. `images:` подменяет логическое имя `catalog` на
реестр и тег; тег `sha-…` неизменяем по соглашению, `latest` в манифестах — никогда. Метки `component` позволяют
применять части overlay по отдельности (`kubectl apply -k … -l …`).

## Опыты руками

```bash
NS="-n hephaestus-04"
# OOMKilled: 200Mi на JVM с 75 % heap
kubectl $NS set resources deploy/catalog --limits=memory=200Mi --requests=memory=200Mi
kubectl $NS get pods -w            # CrashLoopBackOff, describe -> OOMKilled, exit 137
# CPU-троттлинг и availableProcessors
kubectl $NS set resources deploy/catalog --limits=cpu=2 --requests=cpu=500m,memory=768Mi
curl localhost:8080/actuator/runtime   # availableProcessors 2, GC G1? (нужно и >= ~1792 МБ памяти)
# Сломанная readiness: под жив, но без трафика
kubectl $NS patch deploy catalog --type=json \
  -p='[{"op":"replace","path":"/spec/template/spec/containers/0/readinessProbe/httpGet/path","value":"/nope"}]'
kubectl $NS get endpointslices -l kubernetes.io/service-name=catalog
# Изменение конфигурации -> новый хэш -> rollout
echo "LOGGING_LEVEL_ROOT=DEBUG" >> k8s/base/catalog-config.env && scripts/deploy.sh   # затем git checkout
# Вернуть всё
scripts/deploy.sh
```

## Вопросы для собеседования

1. **Liveness vs readiness vs startup?** Рестарт vs снятие с трафика vs защита медленного старта; БД — не в liveness.
2. **Почему деплой даёт 502, хотя есть graceful shutdown?** Удаление из endpoints асинхронно: нужен `preStop`-sleep
   и grace period больше суммы preStop + shutdown.
3. **requests vs limits, QoS?** Планирование vs потолок; Guaranteed/Burstable/BestEffort и порядок выселения.
4. **Что видит JVM при CPU limit 1, и зачем memory request = limit?** 1 процессор, SerialGC; предсказуемый heap, нет выселения.
5. **OOMKilled vs OutOfMemoryError?** Ядро и cgroup (137, без логов) vs heap внутри JVM.
6. **Где запускать миграции?** Отдельный Job/init-контейнер, не при старте каждой реплики; порядок обеспечивает деплой.
7. **Почему Job нельзя «переприменить»?** `spec.template` неизменяем; удалить и создать, или новое имя.
8. **Как поменять конфиг, чтобы поды подхватили?** Хэш в имени ConfigMap (Kustomize/Helm checksum) → rollout.
9. **Deployment vs StatefulSet?** Взаимозаменяемые реплики vs стабильная идентичность и свой том.
10. **Secret безопасен?** Нет, base64; RBAC, шифрование etcd, внешние хранилища (External Secrets, Vault/OpenBao).
