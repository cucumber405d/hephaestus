# 16-cqrs-event-sourcing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Forge of 15 keeps its behaviour but stores work orders and material stock as events in its own PostgreSQL event store, with asynchronous projections, replay, upcasting, time travel and the journal as outbox.

**Architecture:** Copy of 15 without Debezium/Connect (Ordering back to 14's Modulith relay). In Forge: `forge.event` journal + `JdbcEventStore` (stream-version concurrency, global read ordered by `(tx_id, global_position)` below `pg_snapshot_xmin`), two aggregates (`WorkOrder`, `Material`) as pure `decide`/`evolve` functions, a projection runner with locked checkpoints feeding `read.*` tables, and a publisher projection that sends `ForgeEvent` to Kafka.

**Tech Stack:** Java 25, Spring Boot 4.1.2, Spring Modulith 2.1.2 (Ordering), JDBC (`JdbcClient`), Flyway, Jackson (Boot's JSON mapper), Kafka 4.2.2, Avro 1.12.2 + Apicurio 3.3.5, gRPC (Boot), Testcontainers 2, Compose, Strimzi 1.2.0 / CNPG 0.29.0 / Argo CD.

**Spec:** `16-cqrs-event-sourcing/docs/superpowers/specs/2026-11-26-16-cqrs-event-sourcing-design.md`

## Global Constraints

- Packages `de.ibikus.appa.hephaestus.es.<module>`; contract classes keep `de.ibikus.appa.hephaestus.contracts.api.forge.v1` and `...contracts.events`.
- Artifacts `cqrs-es-*`; images `hephaestus/cqrs-es-<module>` (`:jib` locally, `sha-<7>` in GHCR).
- Topics `es.forge.requests`, `es.forge.events` (+ `.DLT`); Apicurio artifacts `es.forge.requests-value`, `es.forge.events-value`; contract 14 unchanged.
- Compose ports: ordering 18080, forge 18081, supplier 18082, Apicurio 18085. No Kafka Connect, no Debezium, no `OUTBOX_MODE`.
- Platform: namespace `hephaestus-16`, host `es.localtest.me`, Argo app `cqrs-event-sourcing`, flag `cqrsEventSourcing: false`.
- Journal `forge.event(global_position bigserial, stream_id, stream_type, version int, type, schema_version int, payload jsonb, metadata jsonb, recorded_at timestamptz, tx_id xid8 default pg_current_xact_id())`, `unique (stream_id, version)`; rows never updated or deleted.
- Checkpoints `read.projection_checkpoint(name, tx_id xid8, position bigint)`; one writer per projection (`SELECT … FOR UPDATE` on its row).
- Projections: `workshop-board`, `smith-load` (`projections.smith-load.enabled`), `stock-ledger`; publisher `forge-events-publisher` (at-least-once, checkpoint after Kafka ack).
- `409` for stream-version conflicts and domain refusals; REST commands never retried automatically.
- Flyway `spring.flyway.default-schema: public` in both services (lesson of 15).
- Comments/YAML/scripts in English; `docs/` in Russian; every commit carries `impacts/impact_<Europe/Berlin timestamp>.md` (YAML name/description, RU+EN).
- Builds are run by Dmitry (`./mvnw -f 16-cqrs-event-sourcing/pom.xml verify`); scripts set `MSYS_NO_PATHCONV=1` only inside `compose()`; scripts committed with mode 100755.

## Review Focus

1. A journal row with an unknown `type` or `schema_version` → the reader throws and the projection stops with an error; never skips (pinned in Task 4).
2. A projection handler failing mid-batch → read model and checkpoint roll back together; the next poll retries the same events (Task 5).
3. Rebuild requested while the runner is catching up → final read model equals a clean rebuild, no duplicate rows (Task 5).
4. A `WorkOrderRequest` redelivered after the work order is DONE → nothing appended, no second `ForgeEvent` (Task 6).
5. `start`/`complete` for an unknown work order → `404`, not `409`/`500`; time travel before the first event → `0`, after the last → current stock (Task 6).

---

### Task 1: 16 as a copy of 15 without CDC

**Files:**
- Create: `16-cqrs-event-sourcing/**` by copying `15-cdc-debezium/` without `target/`, `docs/` (except new spec/plan), `README.md`, `connect/`, `scripts/register-connector.sh`, `scripts/drills.sh`, `pacts/` (regenerated).
- Delete in Ordering: `OutboxWriter`, `OutboxSerializer`, `OutboxCleanup`, `SchemaRegistryUnavailable` handler, `V2__outbox.sql`, `cdc-role.sql`, outbox ITs (`OutboxWriterIT`, `OutboxRegistryDownIT`, `OutboxModeIT`, `OutboxBytesIT`, `OutboxCleanupIT`), `outbox.*` and `cdcUser` properties; `RequestRelay` loses its `@ConditionalOnProperty`.
- Modify: `compose.yaml` (no `connect`, no `wal_level=logical`, no cdc init SQL, no outbox env), `scripts/e2e.sh` (no connector step, no outbox checks; Forge part unchanged for now), `k8s/` (no `connect.yaml`, no cdc secret/role, no logical replication), `16-cqrs-event-sourcing/pom.xml` (artifact `cqrs-es`, description). The root `pom.xml` is not touched.
- Rename: packages `.cdc.` → `.es.`, artifacts `cdc-debezium-*` → `cqrs-es-*`, topics `cdc.forge.*` → `es.forge.*`, namespace/host/flag per Global Constraints.

**Interfaces:**
- Produces: a working 14-style flow under 16's names; Forge still table-based (replaced from Task 6 on).

- [ ] **Step 1:** Copy and rename (scripted, never touching `target/`); grep proves no `cdc`, `debezium`, `connect`, `outbox` left outside `docs/`: `grep -rniE "debezium|kafka connect|outbox\.mode|cdc\." 16-cqrs-event-sourcing --exclude-dir=target --exclude-dir=docs` → empty.
- [ ] **Step 2:** `OrderingFlowIT` runs without `outbox.mode` property (relay always on).
- [ ] **Step 3 (Dmitry):** `./mvnw -f 16-cqrs-event-sourcing/pom.xml clean spotless:apply verify` → BUILD SUCCESS; `16-cqrs-event-sourcing/scripts/e2e.sh` → `E2E OK`.
- [ ] **Step 4:** Commit `feat(16): copy of 15 without CDC — Ordering on the Modulith relay`.

### Task 2: Event store

**Files:**
- Create: `forge-service/src/main/resources/db/migration/V3__event_store.sql` (schema `forge`, table `event`, index `(tx_id, global_position)`, trigger rejecting `UPDATE`/`DELETE`; schema `read`, table `projection_checkpoint`).
- Create: `forge-service/.../es/forge/application/eventstore/` — `EventStore`, `NewEvent`, `RecordedEvent`, `Checkpoint`, `ConcurrencyConflict`; `adapter/out/eventstore/JdbcEventStore`.
- Test: `forge-service/src/test/.../adapter/out/eventstore/EventStoreIT.java`

**Interfaces:**
- Produces:
  - `record NewEvent(String type, int schemaVersion, Object payload, Map<String,String> metadata)`
  - `record RecordedEvent(long position, String txId, String streamId, String streamType, int version, String type, int schemaVersion, JsonNode payload, Map<String,String> metadata, Instant recordedAt)` (`JsonNode` of Boot's Jackson)
  - `record Checkpoint(String txId, long position)` with `static Checkpoint START` (`"0"`, `0`)
  - `interface EventStore { void append(String streamId, String streamType, int expectedVersion, List<NewEvent> events) throws ConcurrencyConflict; List<RecordedEvent> readStream(String streamId); List<RecordedEvent> readAll(Checkpoint after, int limit); }` — `expectedVersion` 0 = new stream; must join the caller's transaction.
  - `class ConcurrencyConflict extends RuntimeException` (stream id, expected version).

- [ ] **Step 1: Failing tests** in `EventStoreIT`:
  - `appendedEventsReadBackInStreamOrder` — versions 1..3, payload/metadata round-trip.
  - `secondWriterWithSameExpectedVersionGetsConflict` — two appends with `expectedVersion=1` → second throws `ConcurrencyConflict`; stream has version 2 only once.
  - `journalRowsCannotBeUpdatedOrDeleted` — `UPDATE`/`DELETE` on `forge.event` throw.
  - `readAllSkipsEventsOfOpenTransactions` — connection A begins and appends (lower position), B appends and commits; `readAll(START)` returns only B's event; after A commits, `readAll(checkpoint of B)` returns A's event.
  - `readAllLosesNothingBetweenPositionsOfOneTransaction` — B begins first and appends (pos p1); A begins and appends (p2, higher xid); B appends (p3) and commits; `readAll(START)` = [p1, p3]; A commits; `readAll(Checkpoint(xid of B, p3))` = [p2].
- [ ] **Step 2:** Run (Dmitry) `./mvnw -f 16-cqrs-event-sourcing/pom.xml -pl forge-service -am verify -Dit.test=EventStoreIT` → FAIL (classes missing).
- [ ] **Step 3:** Implement. `readAll` query: `where (tx_id, global_position) > (?::xid8, ?) and tx_id < pg_snapshot_xmin(pg_current_snapshot()) order by tx_id, global_position limit ?`. Conflict = unique violation on `(stream_id, version)` translated to `ConcurrencyConflict`.
- [ ] **Step 4:** Run → PASS. Commit `feat(16): event store on PostgreSQL — stream concurrency, global order below xmin`.

### Task 3: Aggregates

**Files:**
- Create: `forge-service/.../es/forge/domain/workorder/` — `WorkOrderEvent` (sealed: `WorkOrderRequested(String workOrderId, UUID orderId, String material, int quantity)`, `WorkOrderFailed(String reason)`, `MaterialAllocated(int quantity)`, `WorkOrderStarted(String smith)`, `WorkOrderCompleted()`), `WorkOrderState` (`status`: `NEW`, `WAITING_FOR_MATERIAL`, `ALLOCATED`, `IN_PROGRESS`, `DONE`, `FAILED`), `WorkOrderDecider` (static `decide(Command, State) → List<WorkOrderEvent>`, `evolve(State, WorkOrderEvent) → State`, `INITIAL`), commands `Request`, `Fail`, `Allocate`, `Start(smith)`, `Complete`.
- Create: `.../domain/material/` — `MaterialEvent` (sealed: `StockReceived(int quantity, Source source)` with `Source {INITIAL, SUPPLIER}`, `StockTaken(int quantity, String workOrderId)`), `MaterialState(int onHand)`, `MaterialDecider` (`Receive`, `Take`), `InsufficientStock`.
- Keep: `domain/WorkOrderConflict` semantics move to `domain/workorder/WorkOrderRefused` (message = reason).
- Remove: `domain/WorkOrder.java` record (Task 6 removes its users).
- Test: `domain/workorder/WorkOrderDeciderTest`, `domain/material/MaterialDeciderTest` (given events → when command → then events / exception).

**Interfaces:**
- Produces: deciders above; work order id = `"FW-" + orderId` and it is the stream id; material stream id = `material-<code>`; stream types `WorkOrder`, `Material`.

- [ ] **Step 1: Failing tests:** `requestOnNewGivesRequested`; `requestAgainGivesNothing` (idempotent); `allocateOnlyWhileWaiting`; `startNeedsAllocated` (`WorkOrderRefused` "cannot start"); `startTwiceRefused`; `completeOnlyInProgress`; `failedIsTerminal`; `takeMoreThanOnHandThrowsInsufficientStock`; `receiveThenTakeLeavesRest` (20 received, 5 taken → onHand 15).
- [ ] **Step 2:** Run (Dmitry) `-pl forge-service test -Dtest='*DeciderTest'` → FAIL. **Step 3:** implement. **Step 4:** PASS. Commit `feat(16): WorkOrder and Material as decide/evolve aggregates`.

### Task 4: Event codec and upcasting

**Files:**
- Create: `forge-service/.../adapter/out/eventstore/EventCodec` (type name ↔ record, current schema versions: all 1 except `WorkOrderStarted` = 2), `Upcaster` (`int fromVersion(); String type(); JsonNode up(JsonNode)`), `WorkOrderStartedV1ToV2` (adds `"smith":"unknown"`), `UnknownEvent` (runtime exception).
- Test: `EventCodecTest`.

**Interfaces:**
- Produces: `EventCodec#encode(Object event) → NewEvent`, `EventCodec#decode(RecordedEvent) → Object` (runs upcasters up to current version; unknown type/version → `UnknownEvent`).

- [ ] **Step 1: Failing tests:** `roundTripsEveryEventType`; `startedV1IsReadAsV2WithUnknownSmith`; `unknownTypeThrows`; `newerSchemaVersionThanKnownThrows` (Review Focus 1).
- [ ] **Steps 2–4:** FAIL → implement → PASS. Commit `feat(16): event codec with upcasting (WorkOrderStarted v1 → v2)`.

### Task 5: Projection runner, read models, rebuild

**Files:**
- Create: `forge-service/src/main/resources/db/migration/V4__read_models.sql` — `read.workshop_board(work_order_id pk, order_id, material, quantity, status, smith, failure_reason, last_event_recorded_at, updated_at)`, `read.smith_load(smith pk, in_progress int, done int)`, `read.stock_ledger(material pk, on_hand int)`, `read.stock_movement(position pk, material, delta int, source, work_order_id, recorded_at)`; checkpoint rows for the three projections and `forge-events-publisher` at `('0', 0)`.
- Create: `.../application/projection/Projection` (`String name(); void apply(Object event, RecordedEvent meta); void reset();`), `ProjectionRunner` (`@Scheduled(fixedDelayString="${projections.poll-interval:200ms}")`, per projection: lock checkpoint row, `readAll(checkpoint, ${projections.batch-size:500})`, apply each, save checkpoint — one transaction per batch; disabled projections skipped), `ProjectionAdmin` (`rebuild(name)`: lock, `reset()`, checkpoint → START, same transaction; `status() → List<ProjectionStatus(name, enabled, txId, position, lag)>`).
- Create: `.../adapter/out/projection/WorkshopBoardProjection`, `SmithLoadProjection`, `StockLedgerProjection`; `.../adapter/in/web/ProjectionController` (`POST /admin/projections/{name}/rebuild` → 202, unknown name → 404, publisher → 400; `GET /admin/projections`).
- Test: `ProjectionRunnerIT`, `ProjectionControllerIT`.

**Interfaces:**
- Consumes: `EventStore`, `EventCodec` (Tasks 2, 4).
- Produces: read tables above; `WorkshopBoard#waitingForMaterial() → List<String>` and `WorkshopBoard#list(Optional<String> status) → List<ListedWorkOrder>` (query side used by Task 6).

- [ ] **Step 1: Failing tests** (events appended directly through `EventStore`):
  - `boardShowsLatestStatusAndSmith`; `smithLoadCountsInProgressAndDone`; `stockLedgerSumsMovements`.
  - `failingHandlerRollsBackBatchAndRetries` (handler throws on 2nd event once; afterwards read model complete, no duplicate) — Review Focus 2.
  - `rebuildDuringCatchUpEqualsCleanRebuild` (500 events, rebuild requested from a second thread mid-run) — Review Focus 3.
  - `disabledProjectionCatchesUpWhenEnabled` (spec drill 3 in miniature).
  - `unknownEventStopsProjection` (row with type `Bogus` → checkpoint stays before it, error logged) — Review Focus 1.
  - controller: `rebuildUnknownIs404`, `rebuildPublisherIs400`.
- [ ] **Steps 2–4:** FAIL → implement → PASS. Commit `feat(16): projections with locked checkpoints, rebuild`.

### Task 6: Forge commands on the event store

**Files:**
- Create: `forge-service/src/main/resources/db/migration/V5__journal_seed.sql` — `StockReceived(INITIAL)` IRON-01 20, STEEL-01 20 (MITHRIL-01 none); historical fixture: three work orders `FW-HIST-1..3` (`WorkOrderRequested`, `MaterialAllocated`, `WorkOrderStarted` **schema_version 1** without `smith`, `WorkOrderCompleted`; no material events); publisher checkpoint set to `(pg_current_xact_id(), max(global_position))` of this migration.
- Create: `V6__drop_tables.sql` — drop `work_order`, `material` (`quote` stays).
- Create: `.../application/WorkOrderRepository`, `MaterialRepository` (load = `readStream` + decode + `evolve`; save = `append` with loaded version).
- Modify: `application/WorkOrders` — `request` (idempotent by stream id), quote verification as in 15 → `WorkOrderFailed`, `supply` (allocation: `StockTaken` + `MaterialAllocated` in one transaction; supplier `Delivered` → `StockReceived(SUPPLIER)` first; permanent failure → `WorkOrderFailed` with 15's reasons; transient → stays waiting), `retryWaitingForMaterial()` over `WorkshopBoard#waitingForMaterial()`, `start(id, smith)`, `complete(id)`, `stockAt(material, Instant at) → int` (fold of the `Material` stream with `recordedAt ≤ at`).
- Modify: `adapter/in/web/WorkOrderController` — `POST /api/work-orders/{id}/start?smith=` (required), list from `WorkshopBoard`; new `GET /api/stock/{material}?at=` (no `at` → projection value). `ErrorHandling`: `ConcurrencyConflict`, `WorkOrderRefused` → 409; `WorkOrderNotFound` → 404.
- Modify: `adapter/in/kafka/WorkOrderIntake` — retry on `ConcurrencyConflict` up to 3 times, then rethrow.
- Remove: `JdbcWorkOrderStore`, `JdbcMaterialStock`, `WorkOrderStore`, `MaterialStock`, `Outcomes`, `SpringOutcomes`, `WorkOrderOutcome`, `OutcomeRelay` (publisher replaces it in Task 7), Modulith event registry config in Forge.
- Test: adapt `WorkOrderIntakeIT`, `SmithApiIT`, `QuoteVerificationIT`; new `ConcurrentStartIT`, `TimeTravelIT`, `SupplyIT`.

**Interfaces:**
- Consumes: Tasks 2–5.
- Produces: `WorkOrders#start(String id, String smith)`, `WorkOrders#stockAt(String material, Instant at)`; REST as above.

- [ ] **Step 1: Failing tests:**
  - `ConcurrentStartIT.parallelStartsOneWins` — 10 threads start one allocated work order → 1×200, 9×409; stream has exactly one `WorkOrderStarted`.
  - `WorkOrderIntakeIT.redeliveryAfterDoneAppendsNothing` — Review Focus 4.
  - `SmithApiIT.startUnknownIs404`, `completeUnknownIs404`, `startWithoutSmithIs400` — Review Focus 5.
  - `TimeTravelIT.stockBeforeFirstEventIsZero`, `stockNowEqualsLedger`, `stockAtMiddleExcludesLaterTakes` — Review Focus 5.
  - `SupplyIT.shortfallOrderedFromSupplierThenAllocated`, `unreachableSupplierRetriedLater`, `twoWorkOrdersShareLastStockOneWaits` (material conflict path).
  - `QuoteVerificationIT` assertions read the journal instead of `work_order`.
  - fixture: `historicalStartedV1ShowsUnknownSmithOnBoard`.
- [ ] **Steps 2–4:** FAIL → implement → PASS (Dmitry, whole forge-service). Commit `feat(16): Forge commands append events — work orders and stock from the journal`.

### Task 7: Journal as outbox — ForgeEvent publisher

**Files:**
- Create: `.../adapter/out/events/ForgeEventsPublisher` — scheduled like the runner, own checkpoint `forge-events-publisher`, row lock; maps `WorkOrderCompleted` → `WORK_ORDER_COMPLETED`, `WorkOrderFailed` → `WORK_ORDER_FAILED` with reason, `orderId`/`workOrderId` from the stream (load `WorkOrderRequested`), `eventId` = deterministic UUID from `(stream_id, version)`; other events skipped; sends to `es.forge.events` key = orderId, waits for ack, then saves checkpoint.
- Test: `ForgeEventsPublisherIT` (replaces `OutcomePublicationIT`).

**Interfaces:**
- Consumes: `EventStore`, `EventCodec`, checkpoint table.

- [ ] **Step 1: Failing tests:** `completedAndFailedArePublishedOnce`; `materialAndStartedEventsAreNotPublished`; `historicalFixtureIsNotPublished`; `kafkaDownKeepsCheckpointAndRepublishesLater` (same `eventId` on the resend).
- [ ] **Steps 2–4:** FAIL → implement → PASS. Commit `feat(16): the journal as outbox — ForgeEvent publisher with its own checkpoint`.

### Task 8: Compose scripts — e2e, drills, reconciliation

**Files:**
- Modify: `scripts/e2e.sh` — 15's flow plus: start with `smith=anna`, `GET /admin/projections` shows all at the journal head, `smith-load` has `anna`, `GET /api/stock/IRON-01` equals the ledger and `?at=<before first order>` returns 20.
- Create: `scripts/drills.sh <1..5|all>` (fresh stack per drill, `NOT STAGED` guard, results in `target/drills/results.md`, logs on failure like 15):
  1. parallel `start` race over 20 work orders → per work order exactly one 200;
  2. checksum of `read.workshop_board` → rebuild → same checksum;
  3. `PROJECTIONS_SMITH_LOAD_ENABLED=false`, 30 orders done by 3 smiths, enable + restart Forge → `smith_load` totals match the journal;
  4. orders flowing, `kill -9` forge, start → reconciliation passes, `es.forge.events` has ≥ 1 record per completed order;
  5. 300 orders → projection lag p50/p95 (`updated_at - last_event_recorded_at`).
- Modify: `scripts/reconcile.sh` — accepted custom orders (Ordering) vs `WorkOrderRequested` in `forge.event`; only ids from the drill's `orders.txt`.
- Modify: `scripts/test/reconcile-fixture.sh`.

- [ ] **Step 1:** `scripts/test/reconcile-fixture.sh` cases updated first → run → FAIL; adapt `reconcile.sh` → PASS.
- [ ] **Step 2 (Dmitry, one loop):** `16-cqrs-event-sourcing/scripts/e2e.sh && for n in 1 2 3 4 5; do 16-cqrs-event-sourcing/scripts/drills.sh $n; done 2>&1 | tee 16-cqrs-event-sourcing/target/drills/run.log` → `E2E OK`, five rows in `results.md`.
- [ ] **Step 3:** Commit `feat(16): e2e and drills — races, rebuild, late projection, crash, lag`.

### Task 9: Platform

**Files:**
- Modify: `k8s/` (from Task 1): databases without logical replication, topics `es.forge.*`, services, route `es.localtest.me`, overlay `local`; `scripts/deploy-local.sh`, `scripts/live.sh` (order → work order in the journal → start by `live` → complete → READY; `GET /admin/projections` lag 0).
- Create: `platform/apps/templates/cqrs-event-sourcing.yaml`; `cqrsEventSourcing: false` in `platform/apps/values.yaml`.
- Modify: `scripts/test/k8s-check.py` copy — connector checks only when a `KafkaConnector` exists.

- [ ] **Step 1:** `python3 scripts/test/k8s-check.py <strimzi-crds> <rendered>` → 0 problems; render counts `Cluster:2 KafkaTopic:4 Deployment:4 Job:1 HTTPRoute:1`.
- [ ] **Step 2:** Commit `feat(16): on the platform — CNPG, topics, Apicurio, services (flag cqrsEventSourcing off)`.

### Task 10: Docs, ADRs, CI

**Files:**
- Create: `README.md`, `docs/start.md`, `docs/architecture.md`, `docs/learning-notes.md` (expected drill table, snapshots, upcasting, eventual consistency, GDPR/immutability, when not to use ES, comparison with 10/15); `docs/adr/0017-own-event-store-on-postgresql.md`, `0018-global-order-by-transaction-id.md`, `0019-journal-as-outbox.md`; registry `docs/adr/README.md`; root `README.md` badge.
- Modify: `docs/textbook/questions.md` — expected questions for ES/CQRS.
- Create: `.github/workflows/16-cqrs-event-sourcing.yml` — `verify`; `checks` (executable bits, fixture, schema copies `cmp`, render + `k8s-check.py`, `timeout-minutes: 10`); `e2e`; `drills`; `publish` (supplier-stub, ordering, forge-service; `fail-fast: false`); paths as 15 (+ `publish-image.yml`).

- [ ] **Step 1:** YAML parses; scripts mode 100755 in git; commit `docs(16): README, start, architecture, learning notes, ADR-0017..0019, CI`.
