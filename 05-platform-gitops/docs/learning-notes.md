# Learning notes — 05-platform-gitops

## GitOps

- **Desired state в Git, pull вместо push.** CI не ходит в кластер с `kubectl apply`; агент в кластере (Argo CD)
  сам сравнивает Git с кластером и сводит разницу. Кластеру не нужны креды CI, CI не нужен доступ к кластеру.
- **Drift и self-heal:** `kubectl edit` в обход Git Argo CD откатит (`selfHeal: true`); удалённый из Git манифест
  удалит из кластера (`prune: true`). Аварийная правка — тоже через Git (или временно выключить автосинк).
- **Bootstrap-парадокс:** кто-то должен поставить сам Argo CD. Здесь — один `helm install`, а дальше Application
  `argocd` с тем же чартом и values из Git: Argo CD обновляет сам себя.

## Argo CD

- **Application** = источник (Git/Helm/OCI + ревизия + путь) → назначение (кластер + namespace) + политика синка.
- **App-of-Apps:** корневое Application указывает на чарт, который рендерит дочерние Application. Здесь корень —
  Helm-чарт `platform/apps`, а не каталог YAML: ревизия Git и профиль — values, поэтому CI разворачивает ветку PR
  целиком (у статических YAML ревизия была бы зашита в каждом дочернем Application). Альтернатива — ApplicationSet.
- **Health Application:** с Argo CD 1.8 Application не оценивается как ресурс, и корень не ждал бы детей. В
  `argocd/values.yaml` возвращена health-проверка `argoproj.io_Application` — тогда волны корня значат
  «следующая волна — после того, как предыдущие приложения Healthy».
- **Sync waves и хуки:** внутри синка ресурсы применяются по возрастанию `sync-wave`, следующая волна ждёт health
  предыдущей. Хук `Sync` с `BeforeHookCreation` пересоздаётся на каждом синке — решение проблемы неизменяемого Job из 04.
- **Multi-source:** чарт из Helm-репозитория + values из своего Git (`$values/...`).
- **ServerSideApply:** CRD Envoy Gateway и Strimzi больше лимита аннотации `last-applied-configuration` (256 КБ)
  клиентского apply. **SkipDryRunOnMissingResource:** CR, чей CRD ставит другое Application.
- **OCI-чарты** (Envoy Gateway, Strimzi): `repoURL` без схемы + `chart:`; публичные реестры работают и без объявления
  репозитория, объявление с `enableOCI` — явность и место для кредов приватного реестра.

## Helm и Kustomize

Helm — пакет со своими шаблонами и values (операторы: Argo CD, Envoy Gateway, CNPG, Strimzi); Kustomize —
наложения на готовые YAML (04, Keycloak Operator из upstream). Argo CD умеет оба; собственные компоненты
платформы — простые манифесты, без шаблонизации ради шаблонизации.

## Операторы

Оператор = CRD (новый тип ресурса) + контроллер с reconcile loop: «хочу `Cluster` PostgreSQL на 1 инстанс» →
оператор создаёт поды, PVC, сервисы, секреты, следит за failover и обновлениями. Мы пишем **что**, оператор знает **как**.
- **CloudNativePG:** `Cluster` → поды `catalog-db-1…`, сервисы `catalog-db-rw` (primary), `-ro` (реплики), `-r`
  (любой), секрет `catalog-db-app` (username, password, uri). Бэкапы, реплики, failover — полями `Cluster`.
- **Strimzi:** Kafka 4 — только **KRaft** (ZooKeeper удалён); узлы описывает `KafkaNodePool` (роли
  controller/broker), API `kafka.strimzi.io/v1`. Топики — `KafkaTopic` (Topic Operator), а не auto-create.
- **Keycloak Operator:** `Keycloak` CR → StatefulSet, сервис `keycloak-service`, секрет админа
  `keycloak-initial-admin`; БД — свой CNPG `Cluster`. Realms и клиенты — `KeycloakRealmImport` (07).

## Gateway API

Ingress — один ресурс на всё, возможности — через аннотации конкретного контроллера. Gateway API разделяет роли:
**GatewayClass** (провайдер инфраструктуры: какой контроллер), **Gateway** (оператор кластера: слушатели, хосты,
кто может подключаться — `allowedRoutes`), **HTTPRoute** (команда приложения: хост, пути, бэкенды) — из своего
namespace. Envoy Gateway реализует это на Envoy; `EnvoyProxy` настраивает, как поднимается прокси (здесь —
NodePort 30080 для kind).

## Наблюдаемость

`grafana/otel-lgtm` — один под с Collector, Prometheus, Loki, Tempo, Pyroscope и Grafana: для разработки и
демо, данные в `emptyDir`. Приложения шлют OTLP на `lgtm.observability:4317/4318`. Настоящий стек
(отдельные компоненты, хранение, алерты) — подпроект 11.

## Ресурсы

`minimal` — около 4 ГБ RAM, `full` — 8–10 ГБ (Kafka 1 ГБ, Keycloak 1–1.5 ГБ, otel-lgtm 1–2 ГБ, Argo CD,
операторы, Postgres ×2). Профили и флаги компонентов — чтобы платформа влезала и в ноутбук, и в CI-раннер.

## Вопросы для собеседования

1. **GitOps vs CI-push?** Pull-агент в кластере, Git — источник правды, drift виден и откатывается, нет кредов кластера в CI.
2. **App-of-Apps vs ApplicationSet?** Явный список (корневое приложение) vs генератор по шаблону (каталоги, кластеры, PR).
3. **Как упорядочить деплой в Argo CD?** Sync waves + health; хуки PreSync/Sync/PostSync; health для Application в App-of-Apps.
4. **Как запускать миграции в GitOps?** Sync hook Job с `BeforeHookCreation` (или init-контейнер); не при старте реплик.
5. **Что такое оператор?** CRD + контроллер с reconcile loop; знание эксплуатации в коде (CNPG, Strimzi).
6. **Ingress vs Gateway API?** Аннотации vs типизированные ресурсы и разделение ролей; HTTPRoute между namespaces.
7. **Почему ServerSideApply?** Большие объекты, поле-владение (managed fields), меньше конфликтов с контроллерами.
8. **Как Argo CD управляет сам собой и что будет при ошибке в его values?** Синк сломает Argo CD; починка — `helm upgrade` руками и правка Git.
9. **Kafka без ZooKeeper?** KRaft: метаданные в кворуме контроллеров; роли через node pools.
10. **Где держать секреты в GitOps?** Не в Git открытым текстом: генерируют операторы (CNPG, Keycloak) или External Secrets / Sealed Secrets (21).
