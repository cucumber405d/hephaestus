# 13-modular-monolith Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The legacy "Workshop" monolith in three stages side by side — tangled legacy (Modulith verify red, pinned), modular monolith (verify green, events, Supplier ACL), Forge extracted by Strangler Fig variant A with a runtime cutover and a reconciliation that proves no work order is lost or done twice.

**Architecture:** Maven parent `13-modular-monolith` with modules `supplier-stub`, `legacy`, `modular`, `forge-service`, `traffic`. Monolith ↔ service over Kafka (Modulith externalization out, `@KafkaListener` in, inbox for idempotency). Cutover target decided at publication time from a DB flag. Kafka-free HTTP routing by id format (`WO-` → monolith, rest → service).

**Tech Stack:** Java 25, Spring Boot 4.1.x (root parent), Spring Modulith 2.1.2 (core, jdbc, events-kafka, docs, test), Spring Kafka, PostgreSQL + Flyway, Testcontainers, Jackson XML (Supplier), Jib, Kustomize, Envoy Gateway HTTPRoute, Docker Compose (CI e2e), bash.

**Spec:** `13-modular-monolith/docs/superpowers/specs/2026-11-21-13-modular-monolith-design.md`

## Global Constraints

- Spring Modulith **2.1.2**; Boot from `hephaestus-parent` (4.1.x); no milestone/RC versions.
- Packages `de.ibikus.appa.hephaestus.monolith.<module>`; artifactIds `modular-monolith-<module>`; images `hephaestus/modular-monolith-<module>`.
- Comments/javadoc/YAML/scripts in English; docs in `docs/` in Russian; every commit has `impacts/impact_YYYY_MM_DD_HH.MM.SS.md` (RU+EN, YAML `name`/`description`).
- Config by env vars only; Spring profiles only `local`/`test`.
- Id formats: monolith work orders `WO-<number>` (sequence, zero-padded to 6: `WO-000123`); service `FW-<uuid>`.
- Topics: `forge.requests` (monolith → service: `RemoteWorkOrderRequested`), `forge.events` (service → monolith: `WorkOrderCompleted`, `WorkOrderFailed`); 3 partitions, key = customer order id.
- Supplier protocol (stub and both ACLs): `POST /lieferant/bestellung`, body `<Bestellung><ArtNr>…</ArtNr><Menge>…</Menge></Bestellung>`, answer `<Antwort><Status>N</Status><Liefermenge>…</Liefermenge></Antwort>`; `Status` 0 = delivered, 17 = unknown article, 42 = not deliverable (permanent). Material `MITHRIL-01` is always 42.
- Materials: `IRON-01`, `STEEL-01`, `MITHRIL-01`; seeded by Flyway in every stage that owns Forge (`IRON-01` 20, `STEEL-01` 20, `MITHRIL-01` 0); when stock is short Forge orders the shortfall from Supplier, which delivers it for IRON/STEEL.
- Work order statuses: `PLANNED → IN_PROGRESS → DONE`, or `FAILED` (Supplier permanent failure). Customer order: `ACCEPTED → READY | REJECTED` (reason).
- Cutover flag lives in the monolith DB, changed at runtime via `POST /admin/forge/cutover` (`{"remote":true|false}`) and `POST /admin/forge/decommission`; never an env var.
- Platform namespace `hephaestus-13`; host `workshop.localtest.me`.
- No CI step needs a cluster except the existing kind-free Compose e2e; kind deploy is Dmitry's live run.

## Review Focus

1. A local `WorkOrderRequested` published just before cutover and redelivered after a restart stays local (no Kafka record, one `WO-`) — Task 5 `CutoverIT.publicationBeforeCutoverStaysLocalAfterRepublish`.
2. Kafka redelivers `RemoteWorkOrderRequested` → still exactly one `FW-` work order — Task 6 `WorkOrderIntakeIT.duplicateRequestCreatesOneWorkOrder`.
3. `WorkOrderCompleted` delivered twice to the monolith → order becomes READY once, no exception loop — Task 5 `ForgeEventsIT.duplicateCompletionIsIgnored`.
4. Smith calls `complete` twice / `start` on a DONE order → 409 and no state change — Task 3 and Task 6 `SmithApiIT.repeatedTransitionIsConflict`.
5. Supplier slower than the ACL timeout → transient failure, work order stays PLANNED and is retried; status 42 → FAILED — Task 3 `SupplierAclTest` (WireMock-free: JDK `HttpServer` with delay).
6. `decommission` while open `WO-` exist → 409 with the count, flag unchanged — Task 5 `CutoverIT.decommissionRefusedWhileOpen`.

---

### Task 1: Subproject skeleton and Supplier stub

**Files:**
- Create: `13-modular-monolith/pom.xml` (parent `../pom.xml`, artifactId `modular-monolith`, modules: `supplier-stub`, `legacy`, `modular`, `forge-service`; properties `spring-modulith.version` 2.1.2, `jre.image`, `image.revision`; BOM import of `spring-modulith-bom`; Jib pluginManagement copied from `11-observability/pom.xml` with `hephaestus/modular-monolith-${service.name}:jib`)
- Create: `13-modular-monolith/supplier-stub/` — Spring Boot web app `de.ibikus.appa.hephaestus.monolith.supplier.SupplierStubApplication`, `Bestellung`/`Antwort` records (Jackson XML), `LieferantController`
- Test: `supplier-stub/src/test/java/.../LieferantControllerTest.java` (`@WebMvcTest`)

**Interfaces:**
- Produces: HTTP `POST /lieferant/bestellung` (`application/xml`) per Global Constraints; header `X-Verzoegerung-Ms` (optional) delays the answer — used by e2e only when set.

- [ ] **Step 1: failing test** `LieferantControllerTest`: IRON-01 x5 → `<Status>0</Status><Liefermenge>5</Liefermenge>`; `MITHRIL-01` → Status 42, Liefermenge 0; `FOO` → 17.
- [ ] **Step 2:** `./mvnw -f 13-modular-monolith/pom.xml -pl supplier-stub -am verify` → FAIL (no controller).
- [ ] **Step 3:** implement controller and records.
- [ ] **Step 4:** same command → PASS.
- [ ] **Step 5:** commit `feat(13): subproject skeleton and Supplier stub with its legacy XML protocol` + impact.

### Task 2: `legacy/` — the tangled monolith, violations pinned

**Files:**
- Create: `legacy/` app `de.ibikus.appa.hephaestus.monolith.legacy` with top-level packages `ordering`, `forge`, `inventory`, `supplier` (domain packages, tangled inside); Flyway `V1__legacy.sql` (all tables in schema `public`: `customer_order`, `stock_item`, `work_order`, `material`); REST `POST /api/orders`, `GET /api/orders/{id}`, smith API `GET /api/work-orders`, `POST /api/work-orders/{id}/start`, `POST /api/work-orders/{id}/complete`.
- Test: `LegacyStructureTest`, `LegacyFlowIT` (Testcontainers Postgres + supplier via JDK `HttpServer` fake).

**Interfaces:**
- Produces: request `POST /api/orders` `{"customerId":"c1","sku":"HAMMER-01"}` (stock) or `{"customerId":"c1","custom":true,"material":"IRON-01","quantity":2}`; response 202 `{"id":"<uuid>"}`. Order JSON `{"id","status","reason"}`. Work order JSON `{"id":"WO-000001","orderId","material","quantity","status","origin":"MONOLITH"}`. These shapes are reused unchanged by Tasks 3, 5, 6.

The tangles (all four required, spec "Стадия 0"):
1. `ordering.OrderService` calls `forge.ForgeService` (concrete class) and `forge.ForgeService.complete(...)` updates `customer_order` directly → cycle ordering ↔ forge.
2. `forge` reads `stock_item` through `JdbcTemplate` (inventory's table).
3. `supplier.Antwort`/`Bestellung` (XML classes, German names, numeric codes) are fields/parameters in `forge.WorkOrder` handling.
4. One `@Transactional` spans order creation, work order creation and the Supplier HTTP call.

- [ ] **Step 1: failing tests** — `LegacyStructureTest.verifyFailsWithTheKnownViolations`: `assertThatThrownBy(() -> ApplicationModules.of(LegacyApplication.class).verify())` and the message contains `"Cycle detected"` and each of `ordering`, `forge`; `LegacyFlowIT`: stock order → READY; custom IRON-01 → work order `WO-000001` PLANNED, smith start+complete → order READY; custom MITHRIL-01 → work order FAILED, order REJECTED reason `"material not deliverable: MITHRIL-01"`.
- [ ] **Step 2:** `./mvnw -f 13-modular-monolith/pom.xml -pl legacy -am verify` → FAIL.
- [ ] **Step 3:** implement.
- [ ] **Step 4:** PASS; record the exact `verify()` violation list in `legacy/README.md` (Russian, it is docs) — the story artifact.
- [ ] **Step 5:** commit `feat(13): legacy monolith — four recognisable tangles, verify() red and pinned`.

### Task 3: `modular/` — modules, events, Supplier ACL

**Files:**
- Create: `modular/` app `de.ibikus.appa.hephaestus.monolith.modular` modules `ordering`, `forge`, `inventory`, `supplier` (+ `package-info.java` with `@ApplicationModule` where needed); public API types at module root, internals in `internal` sub-packages; Flyway `V1__modular.sql` with one PostgreSQL **schema per module** (`ordering`, `forge`, `inventory`) plus Modulith `event_publication` in `public`.
- Events (records, module API): `ordering.WorkOrderRequested(UUID orderId, String material, int quantity)`, `forge.WorkOrderCompleted(UUID orderId, String workOrderId)`, `forge.WorkOrderFailed(UUID orderId, String workOrderId, String reason)`.
- ACL: `supplier.SupplierGateway` → `SupplyResult order(String material, int quantity)`; `sealed interface SupplyResult permits Delivered, PermanentFailure, TransientFailure`. XML types only in `supplier.internal`. HTTP timeout env `SUPPLIER_TIMEOUT` (default `2s`); base URL `SUPPLIER_URL`.
- Forge: transient failure keeps work order PLANNED and a `@Scheduled` retry (every 10 s) reorders; permanent → FAILED + `WorkOrderFailed`.
- Test: `ModularStructureTest` (verify + Documenter writing `docs/modules/` into `13-modular-monolith/docs/modules`), `ForgeModuleTest` (`@ApplicationModuleTest`, `Scenario`: `WorkOrderRequested` → work order PLANNED; complete → `WorkOrderCompleted` published), `OrderingModuleTest` (publishes `WorkOrderRequested`; on `WorkOrderCompleted` → READY), `SupplierAclTest`, `ModularFlowIT` (same cases as `LegacyFlowIT`), `SmithApiIT`.

**Interfaces:**
- Consumes: HTTP/JSON shapes of Task 2; Supplier protocol of Task 1.
- Produces: event records above (Task 5 extends ordering's publishing), `forge` smith API identical to Task 2, `WO-` ids from sequence `forge.work_order_seq`.

- [ ] **Step 1: failing tests** — all above; `SupplierAclTest`: status 0 → `Delivered(5)`, 42 → `PermanentFailure("not deliverable")`, 17 → `PermanentFailure("unknown article")`, answer after 3 s with timeout 1 s → `TransientFailure`; `SmithApiIT.repeatedTransitionIsConflict`: complete twice → second 409, status stays DONE; start on DONE → 409.
- [ ] **Step 2:** `-pl modular -am verify` → FAIL.
- [ ] **Step 3:** implement (copy domain logic from legacy, untangle).
- [ ] **Step 4:** PASS; commit `docs/modules/` output.
- [ ] **Step 5:** create `13-modular-monolith/docs/legacy-to-modular.md`: table "нарушение легаси (Task 2 list) → чем лечится в modular". Commit `feat(13): modular monolith — modules own their schemas, events between them, Supplier behind an ACL`.

### Task 4: `forge-service/` core — own DB, smith API, Supplier ACL

**Files:**
- Create: `forge-service/` app `de.ibikus.appa.hephaestus.monolith.forge` (hexagonal: `domain`/`application`/`adapter.in`/`adapter.out`), Flyway `V1__forge.sql` (`work_order`, `material`, `processed_request`, Modulith `event_publication`); materials seeded by Flyway (`IRON-01` 20, `STEEL-01` 20, `MITHRIL-01` 0) — "склад делится при переезде".
- Supplier ACL: copy of Task 3's `supplier` package adapted to `adapter.out.supplier` (copy, no shared library — root rule).
- Smith API identical paths/shapes; ids `FW-<uuid>`; `"origin":"SERVICE"`.
- Test: `SmithApiIT` (same cases as Task 3), `SupplierAclTest` (copy), `ForgeArchitectureTest` (ArchUnit hexagonal rule as in 02).

**Interfaces:**
- Produces: `application.WorkOrders.request(UUID orderId, String material, int quantity) : WorkOrderId` (idempotent per orderId — Task 6 relies on it); events `WorkOrderCompleted`/`WorkOrderFailed` with the same record shapes as Task 3.

- [ ] Steps: failing tests → FAIL → implement → PASS → commit `feat(13): forge-service — Forge with its own database, FW- ids, its own Supplier ACL`.

### Task 5: Monolith cutover — flag, remote publication, events back, decommission

**Files:**
- Create: `modular/.../migration/` module: table `migration.forge_cutover(id int primary key check (id = 1), remote boolean, decommissioned boolean)`; API `ForgeRouting.remote() : boolean`, `ForgeRouting.decommissioned() : boolean`; `MigrationAdminController` `POST /admin/forge/cutover {"remote":bool}`, `POST /admin/forge/decommission`, `GET /admin/forge` → `{"remote","decommissioned","openLocalWorkOrders"}`.
- Modify: `ordering` publishes, in the order transaction, `WorkOrderRequested` (local) when `!remote()`, else `ordering.RemoteWorkOrderRequested(UUID orderId, String material, int quantity)` annotated `@Externalized("forge.requests::#{#this.orderId()}")` (dependency `spring-modulith-events-kafka`, JSON).
- Create: `ordering.internal.ForgeEventsListener` — `@KafkaListener(topics="forge.events", groupId="monolith")`, inbox table `ordering.processed_event(event_id uuid primary key)`; maps to the same handling as local `WorkOrderCompleted/Failed`. Message JSON: `{"eventId","type":"WorkOrderCompleted|WorkOrderFailed","orderId","workOrderId","reason"}`.
- `forge` smith endpoints return 410 when `decommissioned()`; `decommission` refused with 409 `{"openLocalWorkOrders":n}` while `n>0`.
- Monolith internal endpoint for the service's facade: `GET /internal/work-orders?open=true` (local open work orders, Task 2 JSON shape; empty list when decommissioned).
- Test: `CutoverIT` (Testcontainers Postgres + Kafka): `afterCutoverRequestGoesToKafka` (record on `forge.requests`, no `WO-` created), `beforeCutoverStaysLocal`, `publicationBeforeCutoverStaysLocalAfterRepublish` (publish local, mark incomplete, flip flag, call `IncompleteEventPublications.resubmitIncompletePublications` → local work order, no Kafka record), `rollbackSendsNewOrdersLocalAgain`, `decommissionRefusedWhileOpen`; `ForgeEventsIT`: completion from Kafka → READY; `duplicateCompletionIsIgnored`; `failedFromKafkaRejectsOrder`.

**Interfaces:**
- Consumes: Task 3 events; Task 2 JSON.
- Produces: Kafka contracts above (Task 6 consumes `forge.requests` JSON `{"orderId","material","quantity"}` — the Modulith Jackson serialization of the record; Task 6 produces `forge.events` JSON above).

- [ ] Steps: failing tests → FAIL → implement → PASS → commit `feat(13): runtime cutover — target decided at publication, events back from Kafka, rollback and decommission`.

### Task 6: `forge-service/` integration — intake, outbox to monolith, facade list

**Files:**
- Create: `adapter.in.kafka.WorkOrderIntake` `@KafkaListener(topics="forge.requests", groupId="forge-service")` → `WorkOrders.request` (idempotent via `processed_request(order_id primary key)`).
- Create: `adapter.out.events.ForgeEventMessage(UUID eventId, String type, UUID orderId, String workOrderId, String reason)` annotated `@Externalized("forge.events::#{#this.orderId()}")` — published (Modulith outbox) when a work order reaches DONE/FAILED; `eventId` is generated at that moment, `type` is `WorkOrderCompleted` or `WorkOrderFailed` — exactly Task 5's JSON.
- Create: `adapter.out.monolith.MonolithWorkOrders` (ACL to monolith): `List<WorkOrderView> openWorkOrders()` via `GET ${MONOLITH_URL}/internal/work-orders?open=true`, timeout 2 s; on error the list returns service orders only and header `X-Partial: monolith-unavailable`.
- Modify: `GET /api/work-orders` merges service orders + monolith open orders (origin field tells them apart).
- Test: `WorkOrderIntakeIT.duplicateRequestCreatesOneWorkOrder`, `completionIsPublished` (record on `forge.events` with `type":"WorkOrderCompleted"`), `FacadeListIT` (fake monolith via JDK `HttpServer`: merged list; monolith down → own orders + `X-Partial`).

- [ ] Steps: failing tests → FAIL → implement → PASS → commit `feat(13): forge-service takes requests from Kafka, reports back, wraps the monolith's open orders`.

### Task 7: `traffic/`, cutover and reconciliation scripts

**Files:**
- Create: `13-modular-monolith/traffic/` standalone Maven module (no Spring; like `12/load`, not in the parent's `<modules>`; JUnit 5 + AssertJ): `Generator` (POST custom orders at `RATE`/s, material mix 45 % IRON, 45 % STEEL, 10 % MITHRIL; writes `orders.csv` `orderId,material,expected` with expected `DONE`/`FAILED`, LF only), `SmithBot` (polls `GET /api/work-orders` every 1 s, starts PLANNED, completes IN_PROGRESS; routes `WO-` to `MONOLITH_URL` and the rest to `SERVICE_URL` unless `GATEWAY_URL` is set — then everything to the gateway; retries 5xx/IO up to 5 times; 409 = already done, not an error), `Main` (`generate|bot`).
- Create: `scripts/cutover.sh <before|cutover|drain|decommission|rollback>` — calls the monolith admin API (`MONOLITH_URL`); `drain` polls `GET /admin/forge` until `openLocalWorkOrders=0` or `DRAIN_TIMEOUT` (300 s).
- Create: `scripts/reconcile.sh <orders.csv>` — like 12's: dumps via `MONOLITH_ORDERS_DUMP_CMD` (`id|status|reason` from `ordering.customer_order`), `MONOLITH_WO_DUMP_CMD` and `SERVICE_WO_DUMP_CMD` (`order_id|id|status`); joins by `FILENAME == ARGV[1]`, strips `\r`; checks: every CSV order has exactly one terminal work order across both, status = expected, customer order READY for DONE / REJECTED for FAILED, no open work orders, totals; prints `accepted= done= failed= open= missing= double= unexpected=`.
- Test: `traffic` unit tests (`SmithBotTest` with JDK `HttpServer`: routing by prefix, 409 tolerated, retry on 503), `scripts/test/reconcile-fixture.sh` (cases: all good; a double (same order in both); missing; unexpected outcome; empty dump fails; CRLF dumps).

- [ ] Steps: failing tests/fixture → FAIL → implement → PASS → commit `feat(13): traffic generator, smith bot, cutover and reconciliation scripts`.

### Task 8: Compose e2e (CI) and kind deployment (live)

**Files:**
- Create: `compose.yaml` — postgres-monolith, postgres-forge, kafka (KRaft, as 11), supplier-stub, modular, forge-service (images `hephaestus/modular-monolith-*:jib`).
- Create: `scripts/e2e.sh` — `jib:dockerBuild` all four, compose up, wait healthy, start bot + generator (RATE 2), `before` 30 s → `cutover` 30 s → stop generator → `drain` → `decommission` → stop bot → `reconcile.sh` with `docker compose exec` dumps; non-zero on any failure; `KEEP_STACK=1` keeps containers (as 11).
- Create: `k8s/` Kustomize: namespace `hephaestus-13`, CNPG `monolith-db`, `forge-db` (512Mi limit), KafkaTopics `forge.requests`/`forge.events` (ns kafka, 3 partitions), Deployments+Services `modular`, `forge-service`, `supplier-stub`, HTTPRoute host `workshop.localtest.me`: rule 1 path `RegularExpression` `^/api/work-orders/WO-.*` → `modular`; rule 2 `PathPrefix /api/work-orders` → `forge-service`; rule 3 `PathPrefix /` → `modular`. `k8s/overlays/local` sets images `hephaestus/modular-monolith-*:jib`, `imagePullPolicy: IfNotPresent`.
- Create: `scripts/deploy-local.sh` — `jib:dockerBuild`, `kind load docker-image` ×4 into cluster `hephaestus`, `kubectl apply -k k8s/overlays/local`, wait available.
- Create: `scripts/live.sh` — the e2e flow against the cluster (`GATEWAY_URL` via `--resolve` as 12; dumps via `kubectl exec <db>-1 psql`).
- Create: `platform/apps/templates/modular-monolith.yaml` (Application `modular-monolith`, path `13-modular-monolith/k8s`, ns `hephaestus-13`), `components.modularMonolith: false` in `values.yaml` (images not yet in GHCR; enable after first publish, as 06/07).
- Test: CI render check (`kubectl kustomize` counts: Cluster 2, KafkaTopic 2, Deployment 3, HTTPRoute 1); `e2e.sh` run by CI.

- [ ] Steps: render check → write → `bash -n` and render pass → commit `feat(13): Compose e2e for the cutover, kind deployment for the live run`.

### Task 9: Docs, ADRs, CI

**Files:**
- Create: `docs/adr/0009-strangler-fig-new-to-new.md`, `0010-module-boundaries-and-events.md`, `0011-anti-corruption-layers.md` (MADR as 0000); rows in `docs/adr/README.md`.
- Create: `13-modular-monolith/README.md`, `docs/start.md` (build, Compose e2e, kind live: `platform/bootstrap.sh scaling` then `deploy-local.sh`, `live.sh`), `docs/architecture.md` (Mermaid C4 for the three stages + cutover sequence), `docs/learning-notes.md` (interview questions: how to cut a monolith, data in a strangler, Modulith vs ArchUnit, ACL, rollback).
- Create: `.github/workflows/13-modular-monolith.yml` — copy of 11's: `verify` (parent + `traffic` module tests + reconcile fixture + kustomize render), `e2e` (`scripts/e2e.sh`), `publish` matrix `[supplier-stub, modular, forge-service]` on main (`legacy` is not published).
- Modify: root `README.md` badge row for 13.
- [ ] Steps: write → link check (`grep` every relative link target exists) → commit `docs(13): ADR 0009–0011, README, start, architecture, learning notes, CI`.
