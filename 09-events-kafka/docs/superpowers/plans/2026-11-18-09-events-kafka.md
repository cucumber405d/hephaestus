# 09-events-kafka Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ordering publishes OrderPlaced to Kafka; Notification consumes it idempotently with retries, DLT and replay, in a classic consumer group and a share group, as a hands-on Compose playground.

**Architecture:** Ordering copied from 06 (no Keycloak, Inventory replaced by a local always-reserving adapter) gains an `OrderEvents` out-port with a Kafka adapter. New Notification service: hexagonal, own PostgreSQL, one handler used by a classic `@KafkaListener` and (mode `share`) a share listener.

**Tech Stack:** Java 25, Spring Boot 4.1.2, Spring Kafka 4.x, Jackson 3, Flyway, PostgreSQL 17, Kafka `apache/kafka:4.2.2`, Kafka UI `ghcr.io/kafbat/kafka-ui`, Testcontainers 2 (`org.testcontainers.kafka.KafkaContainer`, `testcontainers-kafka`).

**Spec:** `09-events-kafka/docs/superpowers/specs/2026-11-18-09-events-kafka-design.md`

## Global Constraints

- Packages `de.ibikus.appa.hephaestus.eventskafka.{ordering,notification}`; artifactIds `events-kafka`, `events-kafka-{ordering,notification}`; images `hephaestus/events-kafka-{ordering,notification}`; Compose project `hephaestus-09`.
- Topic `orders.events` 3 partitions, key = `customerId`; DLT `orders.events-dlt` 1 partition; header `eventType=OrderPlaced`.
- Event JSON `{eventId, orderId, customerId, lines:[{sku,quantity}], occurredAt}`.
- Producer `acks=all`, idempotence on; Ordering send synchronous with 5 s timeout → 503 `event-publish-failed`.
- Retries: ExponentialBackOff 1 s ×2, 3 retries; non-retryable: deserialization, `InvalidEventException`.
- Groups: `notification` (classic), `notification-share` (share). Ports: Ordering 8080, Notification 8091-8095, Notification-share 8096-8099, Kafka UI 8085, Kafka host 9094.
- English comments; impact file per commit; no k8s.

## Review Focus

1. Same `eventId` twice concurrently (two consumers after a rebalance) → still one notification (PK conflict = duplicate, not error) — Task 3 test.
2. Poison message (not JSON / missing `customerId`) → straight to DLT, no retries, partition not blocked — Task 4 test.
3. Kafka down when ordering → order saved, 503 returned (dual write documented) — Task 2 test.
4. DLT replay when DLT is empty → 200 with `replayed: 0`, returns promptly — Task 4 test.
5. `failCustomers` affects only those customers; others keep flowing (no head-of-line blocking across keys in other partitions) — Task 4 test.

---

### Task 1: Scaffold (Ordering from 06, POMs, CI)
- [ ] Copy `06-service-communication/ordering` → `09-events-kafka/ordering`; rename package `servicecommunication` → `eventskafka`, ids/images per constraints; delete `adapter/out/inventory/**` (+ its tests, `StubInventoryServer`, `OrderingIT` inventory parts) and add `adapter/out/inventory/LocalInventory implements InventoryPort` (reserve → random UUID, available → 100).
- [ ] Parent POM (from 08 minus Resilience4j), modules `ordering`, `notification`; workflow `09-events-kafka.yml` (matrix `[ordering, notification]`); README badge.
- [ ] Commit `feat(09): scaffold Ordering from 06, CI`.

### Task 2: Ordering publishes OrderPlaced
**Produces:** `Order` gains `customerId` (V1 migration column, request field `customerId` `[A-Za-z0-9-]{1,64}`); out-port `OrderEvents { void orderPlaced(Order order) }` → `KafkaOrderEvents` (`KafkaTemplate<String, OrderPlacedEvent>`, record `OrderPlacedEvent`, `NewTopic` beans); `OrderService.place` saves then publishes; `EventPublishException` → 503; `POST /admin/republish/{orderId}` resends with the same `eventId` (eventId stored on the order: column `event_id`).
- [ ] Tests: service publishes after insert (fake), publish failure → exception after save (RF 3); IT `OrderEventsIT` (KafkaContainer + Postgres): record key = customerId, header eventType, JSON fields, partition stable per key; republish sends identical eventId.
- [ ] Commit `feat(09): Ordering publishes OrderPlaced to Kafka`.

### Task 3: Notification service — handler, idempotency, API
**Produces:** domain `Notification`, `OrderPlaced` (consumer-side record), `InvalidEventException`; port `NotificationStore { boolean saveOnce(UUID eventId, Notification n) }` (JPA, one transaction, PK conflict → false); `NotificationHandler.handle(OrderPlaced)`; `@KafkaListener(groupId="notification", topics="orders.events")` (mode `classic`, default); `GET /api/notifications?customerId=`; `Faults` + `/admin/faults` (failCustomers, transientFailures, delayMs) like 08; `/admin/stats` {hostname, processed, duplicates, partitions}.
- [ ] Tests: handler stores once; duplicate → false + counter (RF 1, with two threads); faults: failCustomers throws, transientFailures counts down; IT `NotificationIT` (Kafka + Postgres): event → notification visible via API.
- [ ] Commit `feat(09): Notification service with idempotent consumer`.

### Task 4: Retries, DLT, replay
- [ ] `DefaultErrorHandler(DeadLetterPublishingRecoverer → orders.events-dlt partition 0, ExponentialBackOff(1000, 2.0) maxElapsed for 3 retries)`, `addNotRetryableExceptions(InvalidEventException.class)`, `ErrorHandlingDeserializer`. `POST /admin/dlt/replay` → `DltReplayer` reads DLT with its own consumer (group `notification-dlt-replay`, poll until empty ≤ 2 s), republishes to `orders.events` with original key, commits; returns `{replayed:n}`.
- [ ] IT: transientFailures 2 → success after ≥ 3 s, DLT empty; failCustomers bob → in DLT, alice still notified (RF 5); poison JSON → DLT within < 2 s (RF 2); replay after clearing faults → bob notified; replay on empty DLT → 0 (RF 4).
- [ ] Commit `feat(09): retries, dead letter topic and replay`.

### Task 5: Share group mode
- [ ] `notification.mode=share` → `ShareKafkaListenerContainerFactory` + share listener group `notification-share` calling the same handler; classic listener off (`autoStartup` from mode). Failures → `RELEASE`/redelivery up to the broker's delivery limit (no DLT).
- [ ] IT with Kafka 4.2 container (share groups enabled, `share.coordinator.state.topic.replication.factor=1`, `min.isr=1`): event processed in share mode. If share consumer APIs do not work: ledger ruling, drop scenario 7.
- [ ] Commit `feat(09): share group mode`.

### Task 6: Compose, Kafka UI, e2e
- [ ] `compose.yaml`: kafka (KRaft single node, listeners INTERNAL kafka:9092 / EXTERNAL localhost:9094, share-group settings), kafka-ui 8085, ordering-db, notification-db, ordering, notification (ports `8091-8095:8080`), notification-share (profile `share`, ports `8096-8099:8080`).
- [ ] `scripts/e2e.sh` (+x): scenarios 1–7 of the spec with asserts; scaling via `compose up -d --scale notification=3` / `5`, stats per port; share via `--profile share --scale notification-share=5`.
- [ ] Commit `test(09): Compose with Kafka UI and e2e`.

### Task 7: Docs + final review
- [ ] README, `docs/start.md` (all 8 scenarios by hand), `docs/architecture.md`, `docs/learning-notes.md` (partitions/keys/order, consumer groups & rebalancing, at-least-once + idempotency, retries vs DLT, poison pill, dual write → outbox, share groups vs consumer groups, interview questions).
- [ ] Final review (opus), one fix pass.
