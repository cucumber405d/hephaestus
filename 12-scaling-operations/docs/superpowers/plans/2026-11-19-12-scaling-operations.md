# 12-scaling-operations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The saga of 11 on the shared kind platform survives a Gatling "peak sale" with KEDA autoscaling and disruptions, proven by a reconciliation that no accepted order was lost.

**Architecture:** Platform gains KEDA, metrics-server and a lighter `scaling` profile; `12-scaling-operations/k8s` deploys the four 11 images unchanged (env-only config) with CNPG, KafkaTopics (6 partitions), ScaledObjects, PDBs and an HTTPRoute; a Gatling module drives the load; `peak.sh` runs load + disruption + reconciliation.

**Tech Stack:** kind, Argo CD, Strimzi, CNPG, KEDA (Helm), metrics-server (Helm), Kustomize, Gatling 3.16 Java SDK, bash, psql.

**Spec:** `12-scaling-operations/docs/superpowers/specs/2026-11-19-12-scaling-operations-design.md` and ADR-0001…0007 in `docs/adr/` (accepted).

## Global Constraints

- Images: `ghcr.io/cucumber405d/hephaestus/observability-{ordering,payment,inventory,delivery}:sha-bb81a85` (merge of 11), public, no pull secret.
- Namespace `hephaestus-12`; host `orders.localtest.me` → Ordering via Gateway `platform` in `envoy-gateway-system`.
- Kafka `platform-kafka-bootstrap.kafka:9092`; OTLP `http://lgtm.observability:4318`.
- Saga topics: 8 topics of 10/11 (`ordering.events`, `payment|inventory|delivery.events`, `payment|inventory|delivery.commands`, `ordering.replies`), **6 partitions**, replication 1, as Strimzi `KafkaTopic` in namespace `kafka`.
- Env on all Boot services: `SAGA_STEP_TIMEOUT=120s` (Ordering), `SPRING_KAFKA_CONSUMER_PROPERTIES_GROUP_PROTOCOL=consumer`, `SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE=20s`; pods: `terminationGracePeriodSeconds: 40`, `preStop` sleep 5 s; strategy `maxSurge: 1, maxUnavailable: 0`; PDB `maxUnavailable: 1`.
- KEDA: participants `kafka` trigger on own group, `lagThreshold "20"`, `activationLagThreshold "0"`, min 1, max 6; Ordering min 2, max 6, triggers `cpu` 60 % + `kafka` group `ordering`; HPA scale-down stabilization 120 s.
- ADR-0001 thresholds: settled ≤ 5 min after load; p95 < 500 ms, p99 < 2 s, non-2xx < 0.1 %; zero `timeout at step` rejections; lag 0 ≤ 3 min; replicas at min ≤ 10 min.
- Comments/YAML/scripts in English; docs RU; impact file per commit; ADR registry updated with any ADR change.

## Review Focus

1. A KafkaTopic with 6 partitions must exist **before** the services create theirs with 3 (NewTopic beans): wrong order leaves 3 partitions and caps KEDA at 3 — Task 2, render check of sync waves + `peak.sh` preflight asserts 6 partitions.
2. `group.protocol=consumer` rejects classic-only client settings (e.g. `partition.assignment.strategy`) at startup — Task 2, rollout must reach Ready; Task 5 preflight.
3. Gatling order ids lost if the CSV writer is not thread-safe → reconciliation false-negative — Task 3, `OrderLogTest.concurrentAppendsKeepEveryLine`.
4. Reconciliation must fail on an order still `PENDING` after the window, on a missing id (404) and on an unexpected outcome — Task 4, `reconcile` self-test with a fixture.
5. Drain picks a node hosting Kafka/CNPG/lgtm → platform data loss blamed on 12 — Task 5, node selection unit-tested on fixture `kubectl` output.

---

### Task 1: Platform — KEDA, metrics-server, `scaling` profile

**Files:** Create `platform/apps/templates/keda.yaml`, `platform/apps/templates/metrics-server.yaml`, `platform/apps/templates/scaling.yaml`, `platform/apps/values-scaling.yaml`; Modify `platform/apps/values.yaml` (versions `keda`, `metricsServer`; components `keda`, `metricsServer`, `scaling` = false), `platform/apps/values-full.yaml` (all three true), `.github/workflows/05-platform-gitops.yml` (render loop over `minimal full scaling`), `platform/README.md`.

- [ ] **Step 1:** Versions: KEDA chart pinned to the release supporting the kind node's Kubernetes (check `kubectl version` server on Dmitry's cluster; default 2.21.x for 1.34–1.36, else 2.20.2); metrics-server chart `metrics-server/metrics-server` latest 3.x with `args: [--kubelet-insecure-tls]`. KEDA/metrics-server Applications: sync-wave `-2` (operators), namespaces `keda`, `kube-system`.
- [ ] **Step 2:** `scaling.yaml`: Application `scaling`, path `12-scaling-operations/k8s`, destination namespace `hephaestus-12`, wave `1` (pattern of `service-communication.yaml`).
- [ ] **Step 3:** `values-scaling.yaml`: argocd, gateway, cnpg, observability, strimzi, kafka, keda, metricsServer, scaling = true; catalog, keycloak, redis, serviceCommunication, security = false.
- [ ] **Step 4:** Verify: `for p in minimal full scaling; do helm template platform platform/apps -f platform/apps/values-$p.yaml | grep -c '^kind: Application$'; done` → minimal unchanged, full +3, scaling ≥ 9. (Agent environment may lack helm: then Dmitry runs it.)
- [ ] **Step 5:** Commit `feat(platform): KEDA, metrics-server, scaling profile`.

### Task 2: 12 manifests (Kustomize)

**Files:** Create `12-scaling-operations/k8s/{kustomization.yaml,topics.yaml,ordering.yaml,payment.yaml,inventory.yaml,delivery.yaml,route.yaml,outbox-cleanup.yaml}`.

**Interfaces:** Produces Deployments `ordering|payment|inventory|delivery` (label `app.kubernetes.io/name=<svc>`), CNPG clusters `<svc>-db` (secret `<svc>-db-app`, keys `username`, `password`, `uri`), ScaledObjects `<svc>`, consumer groups = service names.

- [ ] **Step 1:** `topics.yaml`: 8 `KafkaTopic` (`metadata.namespace: kafka`, label `strimzi.io/cluster: platform`, partitions 6, replicas 1), sync-wave `0`.
- [ ] **Step 2:** Per service file: CNPG `Cluster` (wave 0, 1 instance, 1Gi, 256Mi), Deployment (wave 2; image name = service, replaced by kustomization `images`; env from Global Constraints + `SPRING_DATASOURCE_*` from `<svc>-db-app`, `KAFKA_BOOTSTRAP_SERVERS`, `OTLP_ENDPOINT`, Delivery `OTEL_SERVICE_NAME`, `OTEL_EXPORTER_OTLP_ENDPOINT`; probes `/actuator/health/{liveness,readiness}`; requests cpu 250m mem 512Mi, limit mem 640Mi; lifecycle/strategy/grace from Global Constraints; securityContext as in 06), Service port 80→8080, PDB, ScaledObject (Global Constraints; `bootstrapServers`, `consumerGroup` = service, no `topic` → all topics of the group; `advanced.horizontalPodAutoscalerConfig.behavior.scaleDown.stabilizationWindowSeconds: 120`). Flyway runs at app start (as in 11), no migrate Job.
- [ ] **Step 3:** `route.yaml`: HTTPRoute `orders` → Service `ordering:80`, host `orders.localtest.me`, wave 3. `outbox-cleanup.yaml`: one CronJob per service (`0 3 * * *`, `postgres:17`, `psql "$URI" -c "delete from event_publication where completion_date < now() - interval '1 day'"`, `URI` from `<svc>-db-app/uri`, non-root, `concurrencyPolicy: Forbid`).
- [ ] **Step 4:** `kustomization.yaml`: resources + `images` (Global Constraints tag). Verify `kubectl kustomize 12-scaling-operations/k8s > /tmp/12.yaml` and counts: 8 KafkaTopic, 4 Cluster, 4 Deployment, 4 ScaledObject, 4 PodDisruptionBudget, 4 CronJob, 1 HTTPRoute.
- [ ] **Step 5:** Live check (Dmitry): `platform/bootstrap.sh scaling <branch>`; all Applications Synced/Healthy; `kubectl -n hephaestus-12 get deploy` 4 Ready; `curl -s http://orders.localtest.me/api/orders/<random uuid>` → 404.
- [ ] **Step 6:** Commit `feat(12): saga of 11 on the platform — topics, CNPG, KEDA, PDB, route, outbox cleanup`.

### Task 3: Gatling load module

**Files:** Create `12-scaling-operations/load/pom.xml`, `src/test/java/de/ibikus/appa/hephaestus/scaling/load/{PeakSaleSimulation,OrderMix,OrderLog}.java`, `src/test/java/.../OrderMixTest.java`, `OrderLogTest.java`, `src/test/resources/logback-test.xml`.

**Interfaces:** Produces file `target/orders.csv` lines `orderId,style,expected` where `expected` ∈ `CONFIRMED`, `REJECTED:credit limit exceeded`, `REJECTED:not enough stock`; system properties `baseUrl` (default `http://orders.localtest.me`), `warmRate` (2), `peakRate` (20), `peakSeconds` (180).

- [ ] **Step 1: failing tests** — `OrderMixTest.mixIsEightyTenTen` (`OrderMix.of(i)` for i in 0..999: 800 success, 100 credit-limit, 100 out-of-stock; styles alternate choreography/orchestration); `OrderMixTest.successUsesFreshCustomers` (success customers `peak-<i>`, items HAMMER-01/ANVIL-01 qty 1 within the 500 EUR default limit); `OrderLogTest.concurrentAppendsKeepEveryLine` (16 threads × 1000 appends → 16000 distinct lines).
- [ ] **Step 2:** Run `./mvnw -f 12-scaling-operations/load/pom.xml test` → FAIL (classes missing).
- [ ] **Step 3:** `record OrderMix.Order(String customer, String style, String sku, String expected)`, `static Order of(long i)`; `OrderLog.append(String orderId, String style, String expected)` (synchronized append, file from `-DordersFile`, default `target/orders.csv`). Out-of-stock: `TONGS-01`, expected reason `not enough stock`; credit limit: customer `bob`, `ANVIL-01`, reason `credit limit exceeded`.
- [ ] **Step 4:** `PeakSaleSimulation`: `injectOpen(constantUsersPerSec(warmRate).during(60), rampUsersPerSec(warmRate).to(peakRate).during(30), constantUsersPerSec(peakRate).during(peakSeconds), rampUsersPerSec(peakRate).to(warmRate).during(60))`; one `POST /api/orders`, check status 202, save id from `Location`, then `OrderLog.append`; assertions: `global().responseTime().percentile(95).lt(500)`, `percentile(99).lt(2000)`, `global().failedRequests().percent().lt(0.1)`. Plugin `gatling-maven-plugin` (4.x) + `gatling-charts-highcharts` 3.16.x; standalone module (not in a reactor).
- [ ] **Step 5:** Tests PASS; `gatling:test` against a running platform (Dmitry) produces the report and the CSV.
- [ ] **Step 6:** Commit `feat(12): Gatling peak-sale simulation with an order log`.

### Task 4: Reconciliation

**Files:** Create `12-scaling-operations/scripts/reconcile.sh`, `12-scaling-operations/scripts/test/reconcile-fixture.sh` (self-test with a fake API).

**Interfaces:** `reconcile.sh <orders.csv>` — exit 0 only if ADR-0001 items 1–2 hold; prints counts `accepted, confirmed, rejected, pending, missing, unexpected, timeouts`. Env `ORDERS_URL` (default `http://orders.localtest.me`), `WAIT_S` (300).

- [ ] **Step 1: failing self-test** `reconcile-fixture.sh`: `reconcile.sh` reads each order through `ORDER_CMD` (default `curl -fs $ORDERS_URL/api/orders/%s`); the fixture sets `ORDER_CMD='cat fixture/%s.json'` (missing file = 404) for 4 ids: one CONFIRMED as expected, one REJECTED as expected, one PENDING, one missing → `WAIT_S=2 reconcile.sh fixture.csv` exits non-zero and prints `pending=1 missing=1`; a fixture of only matching ids exits 0; a REJECTED with reason `timeout at step payment` → non-zero, `timeouts=1`.
- [ ] **Step 2:** Run → FAIL (no script). **Step 3:** Implement (`ORDER_CMD` + sed like the e2e of 10; polls until no PENDING or `WAIT_S`). Double effects (ADR-0001 item 2): `kubectl exec` into `payment-db-1` / `inventory-db-1` (psql) — sum of `payment_reservation` amounts for confirmed orders' ids equals Ordering's amounts; count of `stock_reservation` rows for confirmed ids equals confirmed count; skipped with a note when `SKIP_DB=true` (fixture).
- [ ] **Step 4:** Self-test PASS (`bash 12-scaling-operations/scripts/test/reconcile-fixture.sh`).
- [ ] **Step 5:** Commit `feat(12): reconciliation — no accepted order lost`.

### Task 5: `peak.sh` — load, disruption, recovery

**Files:** Create `12-scaling-operations/scripts/peak.sh`, `12-scaling-operations/k8s-jobs/stock-refill.yaml`, `12-scaling-operations/scripts/test/drain-node-fixture.sh`.

**Interfaces:** `peak.sh <scale|rollout|kill|drain>`; env `PEAK_RATE`, `PEAK_SECONDS` passed to Gatling.

- [ ] **Step 1: failing self-test** `drain-node-fixture.sh`: `pick_drain_node` reading fixture `kubectl get pods -A -o wide` output → returns the worker without pods of namespaces `kafka`, `observability` or CNPG clusters; fails if both workers host one.
- [ ] **Step 2:** Run → FAIL. **Step 3:** Implement `peak.sh`: preflight (all 8 topics have 6 partitions via `kubectl -n kafka get kafkatopic -o jsonpath`; 4 deployments Ready); `kubectl apply -f k8s-jobs/stock-refill.yaml` + wait (Job: `update stock_item set on_hand = 100000 where sku in ('ANVIL-01','HAMMER-01')`); Gatling in background (`./mvnw -f load/pom.xml gatling:test -DpeakRate=…`); at warm+ramp+peak/2 the disruption (`rollout`: `kubectl rollout restart deploy -n hephaestus-12`; `kill`: delete one pod per participant, one with `--grace-period=0 --force`; `drain`: `kubectl drain $(pick_drain_node) --ignore-daemonsets --delete-emptydir-data --timeout=300s`, later `uncordon`); every 10 s record max replicas per deployment and group lag (`kafka-consumer-groups.sh --describe --all-groups` in pod `platform-dual-role-0`); after Gatling: `reconcile.sh`, then recovery (lag 0 ≤ 180 s; replicas back to min ≤ 600 s); summary table; exit non-zero on any failure.
- [ ] **Step 4:** Self-test PASS; Dmitry runs `peak.sh scale` then `rollout`, `kill`, `drain`.
- [ ] **Step 5:** Commit `feat(12): peak.sh — peak sale with disruptions and recovery checks`.

### Task 6: Scaling dashboard

**Files:** Create `12-scaling-operations/grafana/scaling.json`; Modify `peak.sh` (imports the dashboard via `POST http://grafana.localtest.me/api/dashboards/db`, admin/admin, idempotent `overwrite: true`).

- [ ] **Step 1:** Panels: instances per service `count by (job) (target_info{job=~"ordering|payment|inventory|delivery"})`; consumer lag `max by (job) (kafka_consumer_fetch_manager_records_lag_max)`; accepted/min `sum(rate(http_server_requests_seconds_count{job="ordering",method="POST",status="202"}[1m]))*60` vs completed/min `sum(rate(saga_completed_total[1m]))*60`; p95 of POST; timeouts `saga_timeouts_total`; outbox backlog. Uid `hephaestus-scaling`.
- [ ] **Step 2:** Verify JSON parses; after a run the dashboard shows instances > 1 during the peak (Dmitry).
- [ ] **Step 3:** Commit `feat(12): scaling dashboard`.

### Task 7: Docs, audit, CI

**Files:** Create `12-scaling-operations/README.md`, `docs/{start,architecture,learning-notes,twelve-factor-audit}.md`, `.github/workflows/12-scaling-operations.yml`; Modify root `README.md` (badge), `docs/adr/0005-…` only if CI scope differs (new ADR otherwise).

- [ ] **Step 1:** Audit table per ADR-0007 (factors I–XII + API first, telemetry, authn/z): status, evidence path/command, subproject, gap.
- [ ] **Step 2:** CI: job `verify` = `./mvnw -f 12-scaling-operations/load/pom.xml test` + `kubectl kustomize` render counts + both self-tests; job `e2e` = kind (as 05) + `platform/bootstrap.sh scaling <sha>` + `peak.sh scale` and `peak.sh rollout` with `PEAK_RATE=8 PEAK_SECONDS=90`, timeout 60 min, diagnostics on failure.
- [ ] **Step 3:** Commit `docs(12): README, start, architecture, learning notes, 12-factor audit; CI`.
