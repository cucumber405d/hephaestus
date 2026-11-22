# 13 — запуск

## Сборка и тесты (без кластера)

```bash
./mvnw -f 13-modular-monolith/pom.xml spotless:apply verify
bash 13-modular-monolith/scripts/test/reconcile-fixture.sh
```

`LegacyStructureTest` печатает нарушения Spring Modulith в лог (`grep -A20 "Spring Modulith on the legacy"`);
`ModularStructureTest` переписывает диаграммы в `docs/modules/`.

## Переключение в Docker Compose (то же, что CI)

```bash
13-modular-monolith/scripts/e2e.sh                 # SKIP_BUILD=true, KEEP_STACK=true, RATE=2, BEFORE_S=30, AFTER_S=30
```

Генератор шлёт заказы на изделия на заказ, «кузнец-бот» берёт и завершает заказы на ковку, монолит идёт
`before → cutover → drain → decommission`, в конце — сверка. Файлы прогона: `13-modular-monolith/target/e2e-*`.

Руками, со стеком из `KEEP_STACK=true` (монолит 18080, сервис 18081, поставщик 18082):

```bash
MONOLITH_URL=http://localhost:18080 13-modular-monolith/scripts/cutover.sh state
curl -s localhost:18081/api/work-orders            # фасад: заказы сервиса + открытые WO- монолита
curl -s -X POST localhost:18080/api/work-orders/WO-000001/start
```

## На kind (живой прогон через Envoy Gateway)

До первой публикации образов в GHCR — локальные образы:

```bash
platform/bootstrap.sh scaling                      # Kafka, CNPG, Gateway (если кластера ещё нет)
13-modular-monolith/scripts/deploy-local.sh        # jib:dockerBuild -> kind load -> kubectl apply -k overlays/local
13-modular-monolith/scripts/live.sh                # то же, что e2e, но через http://workshop.localtest.me
```

Маршрут (`k8s/route.yaml`): `^/api/work-orders/WO-.*` → монолит, остальной `/api/work-orders` → сервис, всё прочее →
монолит. После `decommission` правило `WO-` убирается обычным коммитом.

После публикации образов: `components.modularMonolith: true` в профиле платформы и теги `sha-…` в
`k8s/kustomization.yaml` — дальше Argo.

## Шаги переключения

| Шаг | Что делает |
|---|---|
| `before` | новые заказы на ковку — в локальный Forge |
| `cutover` | новые — в сервис (монолит публикует их в Kafka) |
| `drain` | ждёт, пока в монолите не останется открытых `WO-` |
| `decommission` | выключает локальный Forge (отказ, пока что-то открыто); API кузнеца в монолите — 410 |
| `rollback` | после `cutover`: новые снова в монолит, `FW-` доживают в сервисе |
