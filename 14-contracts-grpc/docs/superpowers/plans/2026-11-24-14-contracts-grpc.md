# 14-contracts-grpc Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ordering ↔ Forge from 13 with every contract checkable: a binding gRPC quote (`.proto`, `buf breaking`, Pact with the protobuf plugin), Avro events in Apicurio Registry under `BACKWARD`, an AsyncAPI description, and a script that proves each kind of breaking change is caught at a named step.

**Architecture:** Maven parent `14-contracts-grpc` with modules `contracts` (own fixed version `1.1.0`; generates Java from `.proto` and `.avsc`), `supplier-stub` (copy of 13), `ordering` (from 13's `modular/` without `forge`, `supplier`, `migration`), `forge-service` (from 13 without the monolith facade). Services pin `contracts.version` in their own POM. Kafka values are Avro via the Apicurio SerDe with `auto-register=false`; schemas are registered by CI/compose (`register-schemas.sh`) and checked against `contracts/released/1.0.0/` (the last released contract).

**Tech Stack:** Java 25, Spring Boot 4.1.x (root parent), Spring Modulith 2.1.2, Spring gRPC (1.1.x if its compatibility page lists Boot 4.1, else 1.0.x), grpc-java from that BOM, `io.github.ascopes:protobuf-maven-plugin`, Avro 1.12.x + `avro-maven-plugin`, Apicurio Registry 3.x (`io.apicurio:apicurio-registry-avro-serde-kafka`, image `apicurio/apicurio-registry`), pact-jvm 4.7.x (`au.com.dius.pact.consumer:junit5`, `au.com.dius.pact.provider:junit5`) + `pact-protobuf-plugin`, buf CLI (Docker `bufbuild/buf`), AsyncAPI CLI (Docker `asyncapi/cli`), PostgreSQL + Flyway, Kafka 4.2.2, Testcontainers 2, Jib, Docker Compose, bash.

**Spec:** `14-contracts-grpc/docs/superpowers/specs/2026-11-24-14-contracts-grpc-design.md`

## Global Constraints

- Every external version above is re-checked against Maven Central / GitHub releases at the task that adds it; latest stable only, no milestones/RCs; the chosen version goes into a POM property with a comment `Latest stable on <date>`.
- Packages `de.ibikus.appa.hephaestus.contracts.<module>`; artifactIds `contracts-grpc-<module>`; images `hephaestus/contracts-grpc-<module>:jib`.
- Generated code: proto `java_package = "de.ibikus.appa.hephaestus.contracts.api.forge.v1"`, proto package `hephaestus.forge.v1`; Avro namespace `de.ibikus.appa.hephaestus.contracts.events`, `stringType=String`. No hand-written copies of event/message types in services.
- `contracts` version `1.1.0`; `released/1.0.0/` is frozen; services declare `<contracts.version>1.1.0</contracts.version>` each in its own POM (not the parent).
- Topics `forge.requests` (Ordering → Forge, `WorkOrderRequest`), `forge.events` (Forge → Ordering, `ForgeEvent`); 3 partitions; key = customer order id. Apicurio group `default`, artifact ids by TopicIdStrategy: `forge.requests-value`, `forge.events-value`; rule `COMPATIBILITY=BACKWARD`.
- SerDe: `apicurio.registry.auto-register=false` in both services (exact property names re-checked for v3).
- Quote: gRPC `QuoteService/Quote`, port `9090` (`spring.grpc.server.port`), Ordering deadline `2s`; quote TTL `forge.quote-ttl` default `15m`.
- Price list (Forge config `forge.price-list`, cents per unit / base days): `IRON-01` 1200 / 2, `STEEL-01` 2500 / 3, `MITHRIL-01` 99000 / 30; `forge.units-per-day` 5. `priceCents = rate × quantity`; `leadTimeDays = baseDays + ceil(quantity / unitsPerDay)`. Example pinned in tests and Pact: `IRON-01` × 5 → `priceCents 6000`, `leadTimeDays 3`.
- Rejection reasons (customer order `REJECTED`, exact text): `quote unavailable` (UNAVAILABLE, DEADLINE_EXCEEDED), `unknown material: <code>` (NOT_FOUND), `invalid quantity` (INVALID_ARGUMENT), from Forge events `quote expired`, `quote mismatch`, `quote unknown`, `material not deliverable: MITHRIL-01` (as 13).
- Materials and Supplier protocol exactly as in 13 (`IRON-01` 20, `STEEL-01` 20, `MITHRIL-01` 0; codes 0/17/42).
- Comments/javadoc/YAML/scripts in English; docs in `docs/` in Russian; every commit carries `impacts/impact_YYYY_MM_DD_HH.MM.SS.md` (RU+EN, YAML `name`/`description`); ADRs 0012–0014 with `docs/adr/README.md` updated in the same commit.
- Build: `./mvnw -f 14-contracts-grpc/pom.xml spotless:apply verify`; Docker is needed for Testcontainers, buf and AsyncAPI; the Pact protobuf plugin is installed by `scripts/install-pact-plugin.sh`. No cluster, no platform app (non-goal).

## Review Focus

1. Forge down or slower than 2 s → order `REJECTED` `quote unavailable`, never left `ACCEPTED`, no record on `forge.requests` — Task 5 `OrderServiceTest.quoteUnavailableRejectsWithoutRequest`.
2. A v1 request (no `quoteId`, written with the released 1.0.0 schema) reaching the upgraded Forge → accepted as in 13 (BACKWARD rollout, consumer first) — Task 3 `WorkOrderIntakeIT.v1RequestWithoutQuoteIsAccepted`.
3. Kafka redelivers a v2 request after the quote expired → still one work order, no second `WorkOrderFailed` — Task 4 `QuoteVerificationIT.redeliveryAfterExpiryChangesNothing`.
4. A non-Avro (poison) record on `forge.requests` → `<topic>.DLT`, the next record is still processed — Task 3 `WorkOrderIntakeIT.poisonRecordGoesToDlt`.
5. Quantity `0` or negative → Forge `INVALID_ARGUMENT` → order `REJECTED` `invalid quantity` — Task 4 `QuoteGrpcIT.nonPositiveQuantityIsInvalidArgument`, Task 5 `OrderServiceTest.invalidQuantityRejected`.
6. A scenario patch that no longer applies (code drifted) must fail the script loudly, not count as "caught" — Task 7 `scripts/test/breaking-change-selftest.sh`.

---

### Task 1: Skeleton, `contracts` module, Supplier stub

**Files:**
- Create: `14-contracts-grpc/pom.xml` (parent `../pom.xml`, artifactId `contracts-grpc`, modules in this order: `contracts`, `supplier-stub`, `ordering`, `forge-service`; BOM imports `spring-modulith-bom` 2.1.2, `spring-grpc-dependencies`; Jib pluginManagement copied from 13 with `hephaestus/contracts-grpc-${service.name}:jib`)
- Create: `contracts/pom.xml` (`<version>1.1.0</version>`, protobuf-maven-plugin with the grpc-java plugin, avro-maven-plugin `schema` goal on `src/main/avro`)
- Create: `contracts/src/main/proto/hephaestus/forge/v1/quote.proto`, `contracts/src/main/proto/buf.yaml` (`version: v2`, `breaking.use: [WIRE]`)
- Create: `contracts/src/main/avro/work_order_request.avsc`, `forge_event.avsc`
- Create: `contracts/released/1.0.0/proto/...` (same proto), `contracts/released/1.0.0/avro/...` (request **without** `quoteId`/`priceCents`; event identical)
- Create: `contracts/asyncapi.yaml` (AsyncAPI 3.0.0, both channels, messages `schemaFormat: application/vnd.apache.avro;version=1.9.0`, payload `$ref` to `./src/main/avro/*.avsc`)
- Create: `supplier-stub/` — copy of 13's module, package renamed
- Test: `contracts/src/test/java/.../ContractsRoundTripTest.java`, `MessageSizeTest.java`

**Interfaces:**
- Produces (proto): `service QuoteService { rpc Quote(QuoteRequest) returns (QuoteReply); }`; `QuoteRequest { string material = 1; int32 quantity = 2; }`; `QuoteReply { string quote_id = 1; int64 price_cents = 2; int32 lead_time_days = 3; google.protobuf.Timestamp expires_at = 4; }`.
- Produces (Avro): `WorkOrderRequest { orderId: string(uuid), material: string, quantity: int, quoteId: ["null","string"]=null, priceCents: ["null","long"]=null }`; `ForgeEvent { eventId: string(uuid), type: enum ForgeEventType {WORK_ORDER_COMPLETED, WORK_ORDER_FAILED}, orderId: string(uuid), workOrderId: string, reason: ["null","string"]=null }`.

- [ ] **Step 1: failing tests** `ContractsRoundTripTest`: `v1RequestReadWithCurrentSchemaHasNoQuote` (GenericRecord written with `released/1.0.0/avro/work_order_request.avsc`, read with `WorkOrderRequest.getClassSchema()` → `quoteId == null`, `priceCents == null`, other fields equal); `quoteReplyRoundTrip` (`price_cents 6000`, `lead_time_days 3` survive `toByteArray`/`parseFrom`). `MessageSizeTest.avroIsSmallerThanJson` (same `ForgeEvent` as Avro binary vs Jackson JSON; assert Avro < JSON; print both sizes for learning-notes).
- [ ] **Step 2:** `./mvnw -f 14-contracts-grpc/pom.xml -pl contracts verify` → FAIL (no schemas).
- [ ] **Step 3:** write proto, avsc, released copies, asyncapi.yaml, buf.yaml, POM plugins; copy supplier-stub.
- [ ] **Step 4:** `./mvnw -f 14-contracts-grpc/pom.xml -pl contracts,supplier-stub verify` → PASS.
- [ ] **Step 5:** commit `feat(14): contracts module — quote.proto, Avro events, AsyncAPI, released 1.0.0 baseline` + impact.

### Task 2: Contract checks — registry compatibility, buf, AsyncAPI

**Files:**
- Create: `contracts/src/test/java/.../SchemaCompatibilityIT.java` (Testcontainers `GenericContainer` `apicurio/apicurio-registry:<3.x>`, REST v3 via JDK `HttpClient`, no SDK)
- Create: `scripts/register-schemas.sh <registry-url> <avro-dir>` (idempotent: create artifact if absent, set rule `COMPATIBILITY=BACKWARD`, add a version; non-zero exit on HTTP 409/4xx)
- Create: `scripts/check-proto.sh` (`docker run bufbuild/buf:<ver> breaking src/main/proto --against released/1.0.0/proto` inside `contracts/`), `scripts/check-asyncapi.sh` (`docker run asyncapi/cli:<ver> validate contracts/asyncapi.yaml`)

**Interfaces:**
- Produces: `register-schemas.sh` (used by Task 8 compose `schema-init` and by Task 7); exit codes of `check-proto.sh` / `check-asyncapi.sh` (Task 7, Task 9).

- [ ] **Step 1: failing test** `SchemaCompatibilityIT`: `currentSchemasAreBackwardCompatibleWithReleased` (register released 1.0.0 for both artifacts, set rule BACKWARD, add current → HTTP 200); `requiredFieldWithoutDefaultIsRejected` (add a version of `forge.requests-value` with an extra `{"name":"priority","type":"int"}` → HTTP 409 and the body names the rule).
- [ ] **Step 2:** `./mvnw -f 14-contracts-grpc/pom.xml -pl contracts verify` → FAIL (no IT helper).
- [ ] **Step 3:** implement the IT helper and the three scripts (`set -euo pipefail`, mode 100755 via `git update-index --chmod=+x`).
- [ ] **Step 4:** `./mvnw ... -pl contracts verify` → PASS; `14-contracts-grpc/scripts/check-proto.sh` → exit 0; `14-contracts-grpc/scripts/check-asyncapi.sh` → exit 0 (Dmitry runs these if the agent has no Docker; record output).
- [ ] **Step 5:** commit `feat(14): contract checks — Apicurio BACKWARD, buf breaking (WIRE), AsyncAPI validation` + impact.

### Task 3: `forge-service/` — copy of 13 on Avro

**Files:**
- Create: `forge-service/` from 13 `forge-service/` with package `de.ibikus.appa.hephaestus.contracts.forge`; delete `HttpMonolithWorkOrders`, `MonolithWorkOrders`, `ListedWorkOrder`, the merge in `WorkOrderList`, `MONOLITH_URL`, `FacadeListIT`, `FakeMonolith`.
- Modify: `adapter/in/kafka/WorkOrderIntake` — consumes generated `WorkOrderRequest`; `ErrorHandlingDeserializer` wraps `AvroKafkaDeserializer`; `KafkaErrorHandling` DLT template uses `ByteArraySerializer`.
- Replace: `adapter/out/events/ForgeEventMessage` + `@Externalized` with an internal record `WorkOrderOutcome(UUID eventId, ForgeEventType type, UUID orderId, String workOrderId, String reason)` published by `SpringOutcomes`, and `adapter/out/events/OutcomeRelay` — `@ApplicationModuleListener` that maps to Avro `ForgeEvent` and `kafkaTemplate.send("forge.events", orderId, event).get(10, SECONDS)`.
- Config: `spring.modulith.events.kafka.enable-json` off/removed; value (de)serializer Apicurio; `APICURIO_URL`.
- Test: `test/.../Containers.java` (+ Apicurio container, registers schemas from `../contracts/src/main/avro` via the same REST calls as Task 2), `WorkOrderIntakeIT`, `OutcomePublicationIT`, copied `SmithApiIT`, `SupplierAclTest`, `ForgeArchitectureTest`.

**Interfaces:**
- Consumes: Task 1 generated `WorkOrderRequest`, `ForgeEvent`, `ForgeEventType`.
- Produces: `application.WorkOrders.request(UUID orderId, String material, int quantity, Optional<QuoteRef> quote)` — Task 4 adds `QuoteRef(String quoteId, long priceCents)` and the check; in this task the parameter is accepted and ignored.

- [ ] **Step 1: failing tests** `WorkOrderIntakeIT`: `requestCreatesWorkOrder`, `duplicateRequestCreatesOneWorkOrder`, `v1RequestWithoutQuoteIsAccepted` (bytes produced with a `GenericRecord` of the released 1.0.0 schema registered as version 1), `poisonRecordGoesToDlt` (raw `"not avro"` bytes → `forge.requests.DLT`, then a valid request is still processed). `OutcomePublicationIT.completedWorkOrderEmitsAvroEvent` (smith start+complete → `ForgeEvent` with `WORK_ORDER_COMPLETED`, key = orderId, readable by `AvroKafkaDeserializer`).
- [ ] **Step 2:** `./mvnw ... -pl contracts,forge-service verify` → FAIL.
- [ ] **Step 3:** implement as listed.
- [ ] **Step 4:** same command → PASS (all copied tests too).
- [ ] **Step 5:** commit `feat(14): forge-service on Avro — Apicurio SerDe, outbox relay, DLT for poison records` + impact.

### Task 4: `forge-service/` — binding quote over gRPC

**Files:**
- Create: `domain/PriceList.java` (`Quote price(String material, int quantity)`; throws `UnknownMaterial`, `InvalidQuantity`), `domain/Quote.java` (`String id, String material, int quantity, long priceCents, int leadTimeDays, Instant expiresAt`)
- Create: `application/Quotes.java` (`Quote quote(String material, int quantity)` stores it; `Verdict verify(QuoteRef ref, String material, int quantity, Instant now)` → `OK | UNKNOWN | EXPIRED | MISMATCH`), port `application/QuoteStore.java`, `adapter/out/persistence/JdbcQuoteStore.java`
- Create: `adapter/in/grpc/QuoteGrpcService.java` (`QuoteServiceGrpc.QuoteServiceImplBase`; `UnknownMaterial` → `NOT_FOUND`, `InvalidQuantity` → `INVALID_ARGUMENT`)
- Create: `db/migration/V2__quote.sql` (`quote(id varchar(40) pk, material, quantity, price_cents bigint, lead_time_days int, expires_at timestamptz)`)
- Modify: `WorkOrders.request` — only when the work order is newly created and `quote` is present: verdict ≠ OK → store as `FAILED` with reason `quote unknown|quote expired|quote mismatch` and `outcomes.failed(...)`; absent quote → as 13.
- Config: `forge.price-list`, `forge.units-per-day`, `forge.quote-ttl`, `spring.grpc.server.port: ${GRPC_PORT:9090}`.
- Test: `PriceListTest`, `QuoteGrpcIT` (in-process or random port, real channel), `QuoteVerificationIT`.

**Interfaces:**
- Consumes: Task 1 proto stubs; Task 3 `WorkOrders.request(..., Optional<QuoteRef>)`.
- Produces: gRPC server on 9090 (Tasks 5, 6, 8); `QuoteRef(String quoteId, long priceCents)` in `application`.

- [ ] **Step 1: failing tests** `PriceListTest`: `iron5Costs6000AndTakes3Days`, `steel11Takes6Days` (3 + ceil(11/5)=3 → 6, price 27500), `unknownMaterialThrows`, `zeroQuantityThrows`. `QuoteGrpcIT`: `quoteReturnsPriceLeadTimeAndExpiry` (IRON-01×5 → 6000, 3, `expires_at` ≈ now+15m, `quote_id` non-empty), `unknownMaterialIsNotFound` (`FOO`), `nonPositiveQuantityIsInvalidArgument` (0 and -1). `QuoteVerificationIT`: `validQuoteCreatesWorkOrder`, `expiredQuoteFailsWorkOrder` (clock past expiry → `WORK_ORDER_FAILED` "quote expired"), `priceMismatchFailsWorkOrder` ("quote mismatch"), `unknownQuoteFailsWorkOrder` ("quote unknown"), `redeliveryAfterExpiryChangesNothing` (one work order, one event).
- [ ] **Step 2:** `./mvnw ... -pl contracts,forge-service verify` → FAIL.
- [ ] **Step 3:** implement; `Clock` bean injected so tests move time.
- [ ] **Step 4:** same command → PASS.
- [ ] **Step 5:** commit `feat(14): forge-service quote — fixed price list, gRPC QuoteService, binding quote check at intake` + impact.

### Task 5: `ordering/` — quote first, Avro both ways

**Files:**
- Create: `ordering/` from 13 `modular/` with root package `de.ibikus.appa.hephaestus.contracts.ordering`; keep modules `ordering`, `inventory`; delete `forge`, `supplier`, `migration` and their tests, `V2__cutover.sql`, `RemoteWorkOrderRequested`, `ForgeOutcomes`.
- Modify: `db/migration/V1__ordering.sql` — `customer_order` gains `quote_id varchar(40)`, `price_cents bigint`, `lead_time_days int`.
- Create: `ordering/internal/QuoteClient.java` (`QuoteResult quote(String material, int quantity)`; sealed `QuoteResult = Quoted(String quoteId, long priceCents, int leadTimeDays) | Refused(String reason)`), `ordering/internal/GrpcQuoteClient.java` (channel `forge` from `GrpcChannelFactory`, `withDeadlineAfter(2, SECONDS)`; status → reason per Global Constraints)
- Modify: `OrderService.place` — custom order: quote **before** the DB transaction; `Refused` → insert as `REJECTED` with the reason, no event; `Quoted` → insert `ACCEPTED` with quote columns and publish internal `WorkOrderRequestedEvent(UUID orderId, String material, int quantity, String quoteId, long priceCents)` in the same transaction.
- Create: `ordering/internal/RequestRelay.java` (`@ApplicationModuleListener` → Avro `WorkOrderRequest` → `kafkaTemplate.send("forge.requests", orderId, …).get(10, SECONDS)`)
- Modify: `ForgeServiceOutcomes` — consumes Avro `ForgeEvent` (inbox as in 13), `ErrorHandlingDeserializer`.
- Modify: `CustomerOrder` → `(UUID id, String status, String reason, Long priceCents, Integer leadTimeDays)`.
- Config: `spring.grpc.client.channels.forge.address: ${FORGE_GRPC_ADDRESS:static://localhost:9090}`, Apicurio SerDe, `APICURIO_URL`.
- Test: `OrderServiceTest` (fake `QuoteClient`), `OrderingFlowIT` (Postgres + Kafka + Apicurio; a test gRPC server with a stub `QuoteServiceImplBase`), `ForgeEventsIT`, `OrderingStructureTest` (Modulith verify).

**Interfaces:**
- Consumes: Task 1 stubs and Avro classes; Task 4 server contract.
- Produces: REST `POST /api/orders`, `GET /api/orders/{id}` with `priceCents`, `leadTimeDays` (Task 8 e2e).

- [ ] **Step 1: failing tests** `OrderServiceTest`: `quotedCustomOrderIsAcceptedWithPrice`, `quoteUnavailableRejectsWithoutRequest` (no event published, status REJECTED "quote unavailable"), `unknownMaterialRejected` ("unknown material: FOO"), `invalidQuantityRejected`, `stockOrderSkipsQuote`. `OrderingFlowIT.customOrderSendsAvroRequestWithQuote` (record on `forge.requests` decodes to `WorkOrderRequest` with `quoteId`, `priceCents 6000`). `GrpcQuoteClientTest.slowServerIsUnavailable` (stub sleeps 3 s → `Refused("quote unavailable")` in < 3 s). `ForgeEventsIT`: `completedEventMakesOrderReady`, `duplicateCompletionIsIgnored`, `failedEventRejectsWithReason`.
- [ ] **Step 2:** `./mvnw ... -pl contracts,ordering verify` → FAIL.
- [ ] **Step 3:** implement.
- [ ] **Step 4:** same command → PASS.
- [ ] **Step 5:** commit `feat(14): ordering — binding quote over gRPC before acceptance, Avro requests and events` + impact.

### Task 6: Pact — Ordering's expectations, Forge's verification

**Files:**
- Create: `scripts/install-pact-plugin.sh` (downloads the pinned `pact-protobuf-plugin` release from GitHub into `~/.pact/plugins/protobuf-<ver>/`; no-op if present)
- Create: `ordering/src/test/java/.../QuotePactConsumerTest.java` (`@PactConsumerTest`, provider `forge-service-quote`, `PactSpecVersion.V4`, `usingPlugin("protobuf")`, `pact:proto` = `../contracts/src/main/proto/hephaestus/forge/v1/quote.proto`, `pact:proto-service` = `QuoteService/Quote`)
- Create: `14-contracts-grpc/pacts/ordering-forge-service-quote.json` (written by the consumer test: surefire `pact.rootDir=${project.basedir}/../pacts`; committed)
- Create: `forge-service/src/test/java/.../QuotePactProviderTest.java` (`@Provider("forge-service-quote")`, `@PactFolder("../pacts")`, `PluginTestTarget` with `transport=grpc`, the real `QuoteGrpcService` on a random port; `@State` methods seed the price list)

**Interfaces:**
- Consumes: Task 4 server, Task 5 `GrpcQuoteClient` (the consumer test drives the real client against the Pact mock server).

- [ ] **Step 1: failing tests** consumer interactions: `quote for IRON-01 x5` (response `price_cents 6000`, `lead_time_days 3`, `quote_id` matched by regex `^[0-9a-f-]{36}$`, `expires_at` by type) asserts `Quoted(_, 6000, 3)`; `quote for unknown material` (gRPC status `NOT_FOUND`) asserts `Refused("unknown material: FOO")`. Provider test verifies every interaction in the pact file.
- [ ] **Step 2:** `./mvnw ... verify` → FAIL (no tests' targets yet / no pact file).
- [ ] **Step 3:** implement; module order guarantees `ordering` writes the pact before `forge-service` reads it.
- [ ] **Step 4:** `14-contracts-grpc/scripts/install-pact-plugin.sh && ./mvnw -f 14-contracts-grpc/pom.xml verify` → PASS; `git status --porcelain 14-contracts-grpc/pacts` empty after a second run (file is stable).
- [ ] **Step 5:** commit `test(14): Pact over gRPC — Ordering's quote expectations verified against forge-service` + impact.

### Task 7: Breaking-change scenarios

**Files:**
- Create: `scripts/breaking-change.sh <baseline|1|2|3|4|5|all>` — copies `14-contracts-grpc/` (without `target/`) to `mktemp -d`, applies `scripts/scenarios/<n>-*.patch` with `git apply --check` first (a non-applying patch = exit 2, "scenario drifted"), sets `contracts` and `forge-service`'s `contracts.version` to `1.2.0-scenario`, runs the scenario's check, prints `CAUGHT by <step>` and exits 0 only when that check failed; `baseline` runs all checks unpatched and must pass.
- Create: `scripts/scenarios/1-proto-type-change.patch` (`price_cents` int64 → string) → check `check-proto.sh` must fail.
- Create: `scripts/scenarios/2-proto-drop-lead-time.patch` (remove `lead_time_days`, add `reserved 3;`, remove its setter in `QuoteGrpcService`) → `check-proto.sh` must **pass**, then `./mvnw -pl contracts,forge-service verify` must fail in `QuotePactProviderTest`.
- Create: `scripts/scenarios/3-avro-required-field.patch` (`priority: int` without default in `WorkOrderRequest`) → `./mvnw -pl contracts verify` must fail in `SchemaCompatibilityIT.currentSchemasAreBackwardCompatibleWithReleased`.
- Create: `scripts/scenarios/4-avro-enum-rename.patch` (`WORK_ORDER_FAILED` → `WORK_ORDER_REJECTED`) → same check must fail.
- Create: `scripts/scenarios/5-asyncapi-missing-ref.patch` (payload `$ref` to `./src/main/avro/forge_event_v2.avsc`) → `check-asyncapi.sh` must fail.
- Create: `scripts/test/breaking-change-selftest.sh` — a deliberately broken copy of patch 1 (wrong context line) must exit 2, not 0.

- [ ] **Step 1: failing test** `breaking-change-selftest.sh` → FAIL (no script).
- [ ] **Step 2:** write the script and patches.
- [ ] **Step 3:** `14-contracts-grpc/scripts/test/breaking-change-selftest.sh` → PASS; `14-contracts-grpc/scripts/breaking-change.sh all` → five `CAUGHT by …` lines, exit 0 (Dmitry's run; record output for learning-notes).
- [ ] **Step 4:** commit `test(14): breaking-change scenarios — each change must be caught at its step` + impact.

### Task 8: Compose e2e

**Files:**
- Create: `compose.yaml` — `kafka` (apache/kafka 4.2.2, topics created by an init step: 3 partitions + `.DLT`), `apicurio`, `schema-init` (curl image running `register-schemas.sh http://apicurio:8080 /contracts/src/main/avro`; services `depends_on: condition: service_completed_successfully`), `ordering-db`, `forge-db`, `supplier-stub`, `forge-service` (18081 → 8080, 9090 internal), `ordering` (18080 → 8080, `FORGE_GRPC_ADDRESS=static://forge-service:9090`).
- Create: `scripts/e2e.sh` (`jib:dockerBuild` for the three images, `docker compose up -d --wait`, assertions, `down -v` on exit).

- [ ] **Step 1:** write `e2e.sh` assertions first: IRON-01×5 custom → `ACCEPTED`, `priceCents 6000`, `leadTimeDays 3`; smith start+complete on 18081 → order `READY` within 30 s; MITHRIL-01 → `REJECTED` "material not deliverable: MITHRIL-01"; `FOO` → `REJECTED` "unknown material: FOO"; stock order `HAMMER-01` (as 13) → `READY`; `docker compose stop forge-service` → next custom order `REJECTED` "quote unavailable"; registry `GET .../artifacts/forge.requests-value/rules/COMPATIBILITY` → `BACKWARD`.
- [ ] **Step 2:** run before compose exists → FAIL.
- [ ] **Step 3:** write `compose.yaml`, Jib config per service.
- [ ] **Step 4:** `14-contracts-grpc/scripts/e2e.sh` → `E2E OK` (Dmitry's run).
- [ ] **Step 5:** commit `test(14): Compose e2e — quote, Avro through Apicurio, rejections` + impact.

### Task 9: Docs, ADRs, CI

**Files:**
- Create: `README.md`, `docs/start.md` (prerequisites incl. Docker and `install-pact-plugin.sh`, build, checks, scenarios, e2e), `docs/architecture.md` (diagram, contracts table, rollout order consumer-first), `docs/learning-notes.md` (the scenario table filled from Task 7's output with the "in a shared SNAPSHOT the compiler would catch it" column, Pact vs Spring Cloud Contract, REST vs gRPC, JSON vs Avro with Task 1's sizes, why no Pact Broker yet / `can-i-deploy`).
- Create: `docs/adr/0012-contracts-module.md`, `0013-avro-apicurio-backward.md`, `0014-pact-consumer-driven.md` (MADR); modify `docs/adr/README.md`; update the root README subproject list if it lists statuses.
- Create: `.github/workflows/14-contracts-grpc.yml` — triggers as 13; jobs `verify` (setup-java 25, `install-pact-plugin.sh`, `./mvnw -f 14-contracts-grpc/pom.xml spotless:check verify`, `git diff --exit-code 14-contracts-grpc/pacts`), `checks` (`check-proto.sh`, `check-asyncapi.sh`, `test/breaking-change-selftest.sh`, `breaking-change.sh all`), `e2e` (`e2e.sh`). No publish job.

- [ ] **Step 1:** write docs and ADRs; `grep -c "CAUGHT" docs/learning-notes.md` ≥ 5.
- [ ] **Step 2:** workflow; `actionlint` if available, else YAML parse check (`python3 -c "import yaml,sys; yaml.safe_load(open(sys.argv[1]))"`).
- [ ] **Step 3:** commit `docs(14): README, start, architecture, learning notes, ADR-0012..0014, CI` + impact.
