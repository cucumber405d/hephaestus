# 15-cdc-debezium Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 14's Ordering → Forge flow with the work order request delivered by Debezium from an outbox table (bytes identical to 14's relay), the relay kept as a second mode, failure drills comparing both, and the whole thing on the kind platform with Strimzi Kafka Connect.

**Architecture:** Copy of 14 (`contracts`, `supplier-stub`, `ordering`, `forge-service`) renamed to 15, topics `cdc.forge.requests`/`cdc.forge.events`. Ordering's `OUTBOX_MODE=debezium` writes the Apicurio-serialized `WorkOrderRequest` into `ordering.outbox` inside the order transaction (synchronous listener); `modulith` keeps 14's `RequestRelay`. Kafka Connect (Debezium PostgreSQL connector, pgoutput, Outbox Event Router, `ByteArrayConverter`) publishes the rows. Compose for CI e2e and drills; kind via Argo CD (CNPG logical replication, Strimzi `KafkaConnect`/`KafkaConnector`, Apicurio in `hephaestus-15`).

**Tech Stack:** as 14 (Java 25, Boot 4.1.x, Modulith 2.1.2, Avro 1.12.2, Apicurio 3.3.5, Pact 4.7.5, gRPC from Boot) + Debezium 3.7.x (latest 3.7 patch; image `quay.io/debezium/connect`, plugin `io.debezium:debezium-connector-postgres:<ver>:tar.gz:plugin`), Strimzi 1.2.0 (image `quay.io/strimzi/kafka:1.2.0-kafka-4.3.1` as base of the Connect image), CNPG 0.29 chart, Kustomize, Docker Compose, bash + python3.

**Spec:** `15-cdc-debezium/docs/superpowers/specs/2026-11-25-15-cdc-debezium-design.md`

## Global Constraints

- Versions re-checked at the task that adds them, latest stable, POM property / env default with `Latest stable on <date>`.
- Packages `de.ibikus.appa.hephaestus.cdc.<module>` (generated contract classes keep `de.ibikus.appa.hephaestus.contracts.api.forge.v1` / `...contracts.events`); artifactIds `cdc-debezium-<module>`; images `hephaestus/cdc-debezium-<module>:jib`, Connect image `hephaestus/cdc-debezium-connect`.
- Topics `cdc.forge.requests`, `cdc.forge.events` (+ `.DLT`), 3 partitions, key = customer order id; Apicurio artifacts `cdc.forge.requests-value`, `cdc.forge.events-value`, rule BACKWARD, versions 1.0.0/1.1.0.
- `OUTBOX_MODE` = `debezium` (default) | `modulith`, read at start (`outbox.mode`); in `debezium` no `RequestRelay` bean, in `modulith` no outbox row.
- Outbox table `ordering.outbox(id uuid pk, aggregate_type varchar(64), aggregate_id varchar(64), type varchar(64), payload bytea, created_at timestamptz)`; `aggregate_type = 'forge.requests'`, `aggregate_id` = order id, `type = 'WorkOrderRequest'`; retention `OUTBOX_RETENTION` default `1h`, cleanup every `OUTBOX_CLEANUP_INTERVAL` default `5m`.
- Publication `cdc_outbox` (only `ordering.outbox`), slot `cdc_outbox`, connector name `ordering-outbox`, Debezium user `cdc` (REPLICATION + SELECT on the outbox), `snapshot.mode=no_data`.
- Event Router: route by `aggregate_type`, `route.topic.replacement=cdc.${routedByValue}`, key `aggregate_id`, payload `payload`, `value.converter=org.apache.kafka.connect.converters.ByteArrayConverter`, `key.converter=org.apache.kafka.connect.storage.StringConverter`; delete events (cleanup) produce nothing on the topic.
- The outbox payload is produced by the **same** Apicurio serializer configuration as the relay (built from `spring.kafka.producer.properties`, explicit version `@contracts.version@`): byte-identical record values.
- Platform: namespace `hephaestus-15`, host `cdc.localtest.me`, Argo app `cdc-debezium` (flag `cdcDebezium`, false in all profiles until images are in GHCR); kind runs `debezium` only.
- Ports in Compose: ordering 18080, forge 18081, supplier 18082, Apicurio 18085, Connect 18083, Kafka host 19094, ordering DB 15432 (drills read it).
- Comments/YAML/scripts English; docs Russian; every commit with an impact file; ADR-0015, ADR-0016 + registry README.
- Build `./mvnw -f 15-cdc-debezium/pom.xml spotless:apply verify`; Docker for Testcontainers and Compose; scripts run in Git Bash and Linux (`MSYS_NO_PATHCONV`, `pwd -W` where Docker mounts).

## Review Focus

1. Apicurio down while a quoted custom order is placed (debezium mode) → order not accepted (HTTP 503), no `ordering.outbox` row, no order left `ACCEPTED` — Task 2 `OutboxWriterIT.registryDownRejectsOrderWithoutOutboxRow`.
2. Outbox cleanup deletes rows → no extra record and no tombstone on `cdc.forge.requests` — Task 3 e2e step "one record per order after cleanup".
3. Ordering in `modulith` mode writes no outbox row (a running connector would deliver twice) — Task 2 `OutboxModeIT.modulithModeWritesNoOutboxRow`.
4. A drill that did not actually cause its failure must fail, not pass (stuck count, retained WAL, duplicate count are preconditions) — Task 4 `drills.sh` asserts each precondition; `scripts/test/reconcile-fixture.sh`.
5. Relay bytes and outbox bytes differ (headers vs payload id, other serializer config) — Task 2 `OutboxBytesIT.outboxBytesEqualRelayBytes`.

---

### Task 1: 15 from 14 — copy, rename, topics `cdc.*`

**Files:**
- Create: `15-cdc-debezium/{pom.xml,contracts,supplier-stub,ordering,forge-service,pacts,compose.yaml,scripts/{e2e.sh,register-schemas.sh,install-pact-plugin.sh}}` copied from 14 without `target/`; not copied: `scripts/{breaking-change.sh,check-proto.sh,check-asyncapi.sh,scenarios,test}`, docs of 14.
- Modify: packages `contracts.ordering|forge|supplier` → `cdc.ordering|forge|supplier`; artifactIds `contracts-grpc-*` → `cdc-debezium-*`; image prefix; topic literals and `@KafkaListener` topics, `ForgeTopics`, relays, `KafkaErrorHandling` DLT resolver stays generic; Apicurio artifact ids in `register-schemas.sh`, test `SchemaRegistry`, `SchemaCompatibilityIT`, `asyncapi.yaml` channel addresses; compose project `hephaestus-15`.
- Test: the copied suites.

- [ ] **Step 1:** `grep -rn '"forge\.\(requests\|events\)' 15-cdc-debezium --include=*.java --include=*.yaml --include=*.sh` → no hits outside `cdc.` (RED before the rename).
- [ ] **Step 2:** copy and rename (never touch `target/`; `clean` in every build of this task).
- [ ] **Step 3:** `./mvnw -f 15-cdc-debezium/pom.xml clean spotless:apply verify` → green (Dmitry), pact file regenerated under `15-cdc-debezium/pacts`; `15-cdc-debezium/scripts/e2e.sh` → `E2E OK` (still relay delivery).
- [ ] **Step 4:** commit `feat(15): subproject from 14 — renamed, topics cdc.*` + impact.

### Task 2: Ordering — outbox mode

**Files:**
- Create: `ordering/src/main/resources/db/migration/V2__outbox.sql` (table per Global Constraints, index on `created_at`, `create publication cdc_outbox for table ordering.outbox`, `grant usage on schema ordering, select on ordering.outbox to ${cdcUser}`; Flyway placeholder `cdcUser` default `cdc`, role must exist — compose init / CNPG managed role)
- Create: `ordering/internal/OutboxSerializer.java` (`byte[] serialize(WorkOrderRequest request)`; wraps an `AvroKafkaSerializer` configured from `KafkaProperties.buildProducerProperties()` — the exact map the relay's producer gets — topic `cdc.forge.requests`)
- Create: `ordering/internal/OutboxWriter.java` (`@EventListener` on `WorkOrderRequestedEvent`, synchronous, inside the order transaction; `@ConditionalOnProperty(name = "outbox.mode", havingValue = "debezium", matchIfMissing = true)`; inserts the row)
- Create: `ordering/internal/OutboxCleanup.java` (`@Scheduled(fixedDelayString = "${outbox.cleanup-interval}")`, deletes `created_at < now() - retention`)
- Modify: `RequestRelay` — `@ConditionalOnProperty(name = "outbox.mode", havingValue = "modulith")`; `RequestRelay` and `OutboxWriter` share the Avro mapping `WorkOrderRequests.toAvro(WorkOrderRequestedEvent)` (new package-private helper)
- Modify: `OrderController`/error handling — registry failure during the order transaction → 503 `{"error":"schema registry unavailable"}`
- Modify: `application.yaml` — `outbox.mode: ${OUTBOX_MODE:debezium}`, `outbox.retention: ${OUTBOX_RETENTION:1h}`, `outbox.cleanup-interval: ${OUTBOX_CLEANUP_INTERVAL:5m}`; `@EnableScheduling`
- Test: `OutboxWriterIT`, `OutboxModeIT`, `OutboxBytesIT`, `OutboxCleanupIT`; existing `OrderingFlowIT` runs with `outbox.mode=modulith` (it asserts the relay's record)

**Interfaces:**
- Produces: rows in `ordering.outbox` (Task 3 connector reads them); `OUTBOX_MODE` env (Tasks 3–5).

- [ ] **Step 1: failing tests**
  - `OutboxWriterIT.quotedOrderWritesOneOutboxRow` (debezium mode, stub Forge as in 14): one row, `aggregate_type='forge.requests'`, `aggregate_id`=order id, `type='WorkOrderRequest'`; `payload` deserialized with `AvroKafkaDeserializer` (topic `cdc.forge.requests`) = `WorkOrderRequest` with `quoteId`, `priceCents 6000`; nothing on `cdc.forge.requests` within 3 s.
  - `OutboxWriterIT.registryDownRejectsOrderWithoutOutboxRow` (`apicurio.url` = closed port): POST → 503, no outbox row, no `customer_order` row for that request (count before/after equal).
  - `OutboxModeIT.modulithModeWritesNoOutboxRow` (`outbox.mode=modulith`): record arrives on the topic, outbox empty.
  - `OutboxBytesIT.outboxBytesEqualRelayBytes`: one `WorkOrderRequestedEvent` → `OutboxSerializer.serialize(...)` vs the value bytes of the relay's record read back with `ByteArrayDeserializer` → `assertArrayEquals`.
  - `OutboxCleanupIT.oldRowsAreDeleted` (`outbox.retention=1s`, row with `created_at` 1 h ago, a fresh row): after one cleanup run only the fresh row is left.
- [ ] **Step 2:** `./mvnw ... -pl ordering -am verify` → FAIL.
- [ ] **Step 3:** implement as listed; test `Containers` creates role `cdc` before Flyway (init script on the Postgres container).
- [ ] **Step 4:** same command → PASS.
- [ ] **Step 5:** commit `feat(15): ordering outbox — Avro bytes in the order transaction, relay as the modulith mode` + impact.

### Task 3: Kafka Connect in Compose; e2e in both modes

**Files:**
- Create: `connect/ordering-outbox.json` (connector config per Global Constraints; `database.*` from env placeholders replaced by the registration step)
- Create: `connect/Dockerfile` (FROM `quay.io/strimzi/kafka:1.2.0-kafka-4.3.1`; `USER root`; plugin tarball from Maven Central into `/opt/kafka/plugins/debezium-postgres`; `USER 1001`; `ARG DEBEZIUM_VERSION`) — used by k8s (Task 5), built in CI
- Create: `scripts/register-connector.sh <connect-url>` (POSIX sh, `PUT /connectors/ordering-outbox/config`, idempotent, waits for `RUNNING`)
- Modify: `compose.yaml` — `postgres-ordering` with `command: postgres -c wal_level=logical` and init SQL `create role cdc login replication password 'cdc'`, port 15432; service `connect` (`quay.io/debezium/connect:<ver>`, `BOOTSTRAP_SERVERS`, `GROUP_ID`, `*_STORAGE_TOPIC`, `OFFSET_FLUSH_INTERVAL_MS: ${OFFSET_FLUSH_INTERVAL_MS:10000}`, port 18083); `connector-init` (curl image, `service_completed_successfully` before nothing — ordering may start first); ordering `OUTBOX_MODE: ${OUTBOX_MODE:-debezium}`, `OUTBOX_RETENTION: ${OUTBOX_RETENTION:-1h}`, `OUTBOX_CLEANUP_INTERVAL: ${OUTBOX_CLEANUP_INTERVAL:-5m}`
- Modify: `scripts/e2e.sh` — `OUTBOX_MODE` passed through; new checks: connector `RUNNING` (debezium); after the flow, with `OUTBOX_RETENTION=5s OUTBOX_CLEANUP_INTERVAL=5s` and 15 s wait, exactly one record per order key on `cdc.forge.requests` (kafka-console-consumer in the Kafka container, `print.key=true`), outbox table empty (debezium)

- [ ] **Step 1:** write the new e2e assertions first; run → FAIL (no connect service).
- [ ] **Step 2:** compose, connector JSON, registration script.
- [ ] **Step 3:** `OUTBOX_MODE=debezium 15-cdc-debezium/scripts/e2e.sh` → `E2E OK`; `OUTBOX_MODE=modulith 15-cdc-debezium/scripts/e2e.sh` → `E2E OK` (connector checks skipped) (Dmitry).
- [ ] **Step 4:** commit `feat(15): Debezium Kafka Connect in Compose; e2e in both outbox modes` + impact.

### Task 4: Drills

**Files:**
- Create: `scripts/drills.sh <1..5|all> <debezium|modulith>` (starts the stack with the mode, places orders with a small generator — custom IRON-01×1 every 200 ms, ids to `target/drills/<run>/orders.txt` — causes the failure, recovers, reconciles; prints one table row `drill | mode | precondition | recovered by | missing before/after | duplicates | seconds`; appends to `target/drills/results.md`)
- Create: `scripts/reconcile.sh <orders.txt>` → `missing=<n>` (accepted custom orders with a quote and no work order in forge; reads both DBs via `docker compose exec psql -Atc`) exit 0 only when 0
- Create: `scripts/test/reconcile-fixture.sh` (feeds canned query outputs through `RECONCILE_FAKE_DIR`; cases: all present → 0; one missing → 1 and `missing=1`; duplicates in Forge impossible (unique) → ignored)
- Drill definitions (precondition must hold or the drill fails `NOT STAGED`):
  1. `docker compose pause kafka`; 30 s of orders; `unpause`; wait 60 s. debezium: precondition = orders accepted while paused > 0; reconcile must pass without touching Ordering. modulith: precondition = `missing > 0` after the wait; `docker compose restart ordering`; wait 60 s; reconcile must pass. Row says `recovered by: itself` / `restart of ordering`.
  2. `pause kafka`; 10 s of orders; `kill -s SIGKILL ordering`; `unpause kafka`; wait 30 s. debezium: reconcile passes **while Ordering is down**; modulith: precondition `missing > 0`, `start ordering`, wait, reconcile passes.
  3. debezium only: `stop connect`; 60 s of orders; precondition: retained WAL of slot `cdc_outbox` (`pg_wal_lsn_diff(pg_current_wal_lsn(), confirmed_flush_lsn)`) grew; `start connect`; wait; reconcile passes; retained WAL back below the first sample + 1 MB.
  4. debezium only, `OFFSET_FLUSH_INTERVAL_MS=60000`: 20 s of orders; `kill -s SIGKILL connect`; `start connect`; precondition: some key has more than one record on `cdc.forge.requests`; reconcile passes and Forge has exactly one work order per order (unique key).
  5. both modes: 300 orders at 5/s; latency = Forge `work_order.created_at` − Ordering `customer_order.created_at`, p50/p95 (python3); no precondition.

- [ ] **Step 1:** `scripts/test/reconcile-fixture.sh` → FAIL (no reconcile.sh).
- [ ] **Step 2:** write reconcile.sh, drills.sh.
- [ ] **Step 3:** fixture → PASS; `15-cdc-debezium/scripts/drills.sh all debezium` and `... all modulith` → every row `OK`, results table written (Dmitry).
- [ ] **Step 4:** commit `test(15): failure drills — the relay against Debezium` + impact.

### Task 5: Platform

**Files:**
- Create: `k8s/topics.yaml` (KafkaTopics `cdc-forge-requests`, `cdc-forge-events` and their DLTs, namespace kafka)
- Create: `k8s/app/databases.yaml` (CNPG `ordering-db` with `postgresql.parameters.wal_level: logical`, `managed.roles: [{name: cdc, login: true, replication: true, passwordSecret: {name: cdc-credentials}}]`; `forge-db` as 13)
- Create: `k8s/app/secrets.yaml` (`cdc-credentials`, type `kubernetes.io/basic-auth`, a dev password — learning cluster; note in docs)
- Create: `k8s/app/registry.yaml` (Apicurio Deployment + Service, in-memory; Job `schema-init` running `register-schemas.sh` from a ConfigMap built by `configMapGenerator` with the `.avsc` files; sync-wave 1, services wave 2)
- Create: `k8s/app/connect.yaml` (`KafkaConnect` `cdc-connect`: `image` = Connect image, `bootstrapServers: platform-kafka-bootstrap.kafka:9092`, annotation `strimzi.io/use-connector-resources: "true"`, storage topics with replication 1, `config.providers: secrets` with `io.strimzi.kafka.KubernetesSecretConfigProvider`; Role/RoleBinding letting its service account read `cdc-credentials`; `KafkaConnector` `ordering-outbox` with the config from Task 3, password `${secrets:hephaestus-15/cdc-credentials:password}`, `database.hostname: ordering-db-rw`)
- Create: `k8s/app/{services.yaml,route.yaml,kustomization.yaml}`, `k8s/kustomization.yaml`, `k8s/overlays/local/kustomization.yaml` (as 13; `imagePullPolicy: Never`; `OUTBOX_MODE=debezium`; `FORGE_GRPC_TARGET=static://forge-service:9090`)
- Create: `scripts/deploy-local.sh` (jib:dockerBuild + `docker build connect/` + `kind load` ×4 + two applies + waits for clusters, KafkaConnect `Ready`, KafkaConnector `Ready`), `scripts/live.sh` (via `cdc.localtest.me`: custom order → READY within 60 s; connector status)
- Create: `platform/apps/templates/cdc-debezium.yaml` (as `modular-monolith.yaml`, path `15-cdc-debezium/k8s`, namespace `hephaestus-15`); `cdcDebezium: false` in `values.yaml`

- [ ] **Step 1:** CI-style render check first: `kubectl kustomize 15-cdc-debezium/k8s` contains `Cluster:2 KafkaTopic:4 KafkaConnect:1 KafkaConnector:1 Deployment:4 Job:1 HTTPRoute:1`; local overlay has no KafkaTopic → FAIL (no files).
- [ ] **Step 2:** write the manifests and scripts.
- [ ] **Step 3:** render check → PASS; `platform/bootstrap.sh scaling` then `15-cdc-debezium/scripts/deploy-local.sh` and `scripts/live.sh` → `LIVE OK` (Dmitry).
- [ ] **Step 4:** commit `feat(15): on the platform — CNPG logical replication, Strimzi Kafka Connect, Apicurio` + impact.

### Task 6: Docs, ADRs, CI

**Files:**
- Create: `README.md`, `docs/{start,architecture,learning-notes}.md` (learning-notes: the drill table from `target/drills/results.md` of Dmitry's run, WAL/slot risk and `max_slot_wal_keep_size`, at-least-once and idempotent Forge, comparison with 10), ADR-0015, ADR-0016 + `docs/adr/README.md`; root README badge.
- Create: `.github/workflows/15-cdc-debezium.yml` — `verify` (Pact plugin, `verify`, pact guard with `git status --porcelain`), `checks` (reconcile fixture, kustomize render as Task 5 Step 1), `e2e` (both modes, matrix), `drills` (`all debezium`, `all modulith`, upload `target/drills`), `publish` on main (Jib ×3 via `publish-image.yml`, Connect image via `docker/build-push-action` to `ghcr.io/<owner>/hephaestus/cdc-debezium-connect:sha-<7>`).

- [ ] **Step 1:** docs and ADRs; workflow; YAML parse check.
- [ ] **Step 2:** commit `docs(15): README, start, architecture, learning notes, ADR-0015/0016, CI` + impact.
