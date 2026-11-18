# 11-observability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The saga of 10, observable end to end: one trace per order through HTTP, Kafka and the outbox; Boot instrumentation vs the Java agent side by side; saga metrics, JSON logs in Loki, Grafana dashboard and alert.

**Architecture:** A copy of the four 10 services. Ordering, Payment and Inventory use `spring-boot-starter-opentelemetry`; their outbox is relayed to Kafka by an own `@ApplicationModuleListener` (instead of Modulith's Kafka externalizer) that continues the trace stored in the message. Delivery is unchanged code with the OpenTelemetry Java agent in its image. Everything exports OTLP to one `grafana/otel-lgtm` container in Compose.

**Tech Stack:** Java 25, Spring Boot 4.1.2, Spring Modulith 2.1.1, Micrometer Tracing (OTel bridge), OpenTelemetry Java agent 2.32.0, OpenTelemetry Logback appender 2.32.0-alpha, `grafana/otel-lgtm:0.29.2`, Kafka 4.2.2, PostgreSQL 17, Testcontainers 2, Jib.

**Spec:** `11-observability/docs/superpowers/specs/2026-11-18-11-observability-design.md` (business behaviour: `10-outbox-saga/docs/superpowers/specs/2026-11-18-10-outbox-saga-design.md`).

## Global Constraints

- Packages `de.ibikus.appa.hephaestus.observability.<service>`; artifacts `observability-<service>`; images `hephaestus/observability-<service>`; Compose project `observability`.
- Business behaviour, API, topics, levers identical to 10; all tests of 10 stay green in the copy.
- Ports: services `127.0.0.1:8080-8083`; Grafana `127.0.0.1:3000` (admin/admin); Tempo `127.0.0.1:3200`, Prometheus `127.0.0.1:9090`, Loki `127.0.0.1:3100`, OTLP `127.0.0.1:4318` — all from the `lgtm` container.
- OTLP endpoint from env `OTLP_ENDPOINT` (default `http://localhost:4318`); sampling 100 %.
- Switch `observability.outbox.carry-trace` (env `OUTBOX_CARRY_TRACE`, default `true`).
- Delivery: no telemetry dependency and no telemetry code; agent configured by `OTEL_*` env only.
- Without lgtm/collector every service works as in 10 (tests run with OTLP pointed at a closed port).
- Span attributes `order.id`, `saga.style`, `saga.step`, `message.type`; metrics `saga.completed{style,outcome,reason}`, `saga.duration{style}`, `saga.step.duration{style,step}`, `saga.timeouts{step}`, `outbox.publications.incomplete`.
- Comments/javadoc/YAML/scripts in English; docs RU; impact file per commit (CLAUDE.md); `spotless:apply` before commit.

## Review Focus

1. Kafka down when the relay sends → publication stays incomplete and the resubmitted send carries the **original** trace id — Task 3, `OutboxRelayIT.resubmittedPublicationKeepsTheTrace`.
2. Stored `traceparent` null or malformed (republish, old row, carry-trace off) → message is sent without a parent, no exception — Task 3, `TraceCarrierTest`.
3. OTLP endpoint unreachable → no failed request, no startup failure — all ITs run against `http://127.0.0.1:1` (Task 2 test config).
4. Delivery (agent) receives Boot-produced headers and its replies stay in the trace on first send — e2e scenario 1 (Task 7).
5. Saga timeout fired by the scheduler in another thread → compensations in the saga's trace — Task 4, `OrderingIT.timeoutCompensationsStayInTheSagaTrace`.

---

### Task 1: Copy 10 into 11

**Files:** Create `11-observability/{pom.xml,ordering,payment,inventory,delivery,postgres,compose.yaml,scripts/e2e.sh}` from `10-outbox-saga/`; Modify root `pom.xml` (module `11-observability`).

- [ ] **Step 1:** Copy the tree (without `target`, `docs`, `README.md`), rename packages `outboxsaga` → `observability`, artifactIds `outbox-saga*` → `observability*`, images `hephaestus/outbox-saga-*` → `hephaestus/observability-*`, Compose project name, Spring Modulith/Kafka config unchanged.
- [ ] **Step 2:** Verify nothing of 10 is left: `grep -rn 'outbox-saga\|outboxsaga' 11-observability --include=*.java --include=*.xml --include=*.yaml --include=*.sh` → no output.
- [ ] **Step 3:** Commit `feat(11): copy of the 10 saga as the observability subject` (+ impact). Build check by Dmitry: `./mvnw -f 11-observability/pom.xml spotless:apply verify` → BUILD SUCCESS, same test count as 10.

### Task 2: Boot instrumentation in Ordering, Payment, Inventory

**Files:** Modify the three `pom.xml`, `application.yaml`, test `config/application.properties`; Create `src/main/resources/logback-spring.xml`, `messaging/LogExport.java` in each; Modify `Probe.java` (all four) — add `readWithTrace`.

**Interfaces:**
- Produces: `Probe.readWithTrace(String bootstrap, Messages messages, String topic, UUID orderId) -> List<Probe.Received>`; `record Received(SagaMessage message, String traceId)` (`traceId` = 2nd field of the `traceparent` header, `null` if absent).

- [ ] **Step 1: failing test** `PaymentIT.repliesCarryATraceparent`: send a `PROCESS_PAYMENT` command (no header), `await` the reply on `ordering.replies`, assert `readWithTrace(...).getFirst().traceId()` is 32 hex chars. Test class gets the Boot 4 test annotation that enables tracing (`@AutoConfigureTracing`; Boot 3's `@AutoConfigureObservability`).
- [ ] **Step 2:** Run `./mvnw -f 11-observability/pom.xml -pl payment verify -Dit.test=PaymentIT` → FAIL (no header).
- [ ] **Step 3:** Dependencies: `spring-boot-starter-opentelemetry`, `io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0:2.32.0-alpha` (version property `otel.instrumentation.version` in the 11 parent). YAML:
  ```yaml
  spring.kafka.template.observation-enabled: true
  spring.kafka.listener.observation-enabled: true
  management:
    tracing.sampling.probability: 1.0
    opentelemetry:
      tracing.export.otlp.endpoint: ${OTLP_ENDPOINT:http://localhost:4318}/v1/traces
      logging.export.otlp.endpoint: ${OTLP_ENDPOINT:http://localhost:4318}/v1/logs
    otlp.metrics.export:
      url: ${OTLP_ENDPOINT:http://localhost:4318}/v1/metrics
      step: 10s
  ```
  `logback-spring.xml`: Boot `defaults.xml` + `console-appender.xml` (keeps the ECS JSON console from `logging.structured.format.console`) + appender `OTEL` = `io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender`, root INFO → CONSOLE, OTEL. `LogExport` (`@Component`, `InitializingBean`): `OpenTelemetryAppender.install(openTelemetry)`. Test properties: `OTLP_ENDPOINT=http://127.0.0.1:1`.
- [ ] **Step 4:** Same run → PASS; whole `verify` green.
- [ ] **Step 5:** Commit `feat(11): Boot OpenTelemetry in ordering, payment, inventory`.

### Task 3: Trace-carrying outbox relay

**Files (Ordering, Payment, Inventory, identical kit):** Create `messaging/TraceCarrier.java`, `messaging/OutboxRelay.java`; Modify `messaging/OutgoingMessage.java`, `messaging/Outbox.java`, `messaging/OutboxConfiguration.java` (drop `EventExternalizationConfiguration`, add `@EnableAsync`), poms (drop `spring-modulith-events-kafka`), yaml (drop `spring.modulith.events.kafka`, add `observability.outbox.carry-trace: ${OUTBOX_CARRY_TRACE:true}`). Test: `ordering/.../messaging/TraceCarrierTest.java`, `ordering/.../messaging/OutboxRelayIT.java` (the kit is identical; tested once in Ordering).

**Interfaces:**
- Produces: `record OutgoingMessage(String topic, SagaMessage message, String traceparent)`; `TraceCarrier`: `String capture()` (current context as W3C `traceparent`, `null` if switch off or no span), `<T> T continueTrace(String traceparent, String spanName, Supplier<T> work)` (switch on and valid parent → new span with that parent, in scope; otherwise just `work.get()`), `void tag(SagaMessage m)` (sets `order.id`, `saga.style`, `message.type` on the current span, no-op without span), `void tag(String key, String value)`. Uses Micrometer `Tracer` + `Propagator`.
- `OutboxRelay.relay(OutgoingMessage)` — `@ApplicationModuleListener`; `carrier.continueTrace(outgoing.traceparent(), "outbox relay", () -> kafka.send(topic, orderId, json).get(10, SECONDS))`; exception propagates → publication stays incomplete → `OutboxResubmitter` re-invokes it.

- [ ] **Step 1: failing tests**
  - `TraceCarrierTest`: `continueTraceWithValidParentUsesItsTraceId` (`00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01` → span trace id `4bf92f3577b34da6a3ce929d0e0e4736`); `nullOrMalformedParentRunsWorkWithoutException` (`null`, `"garbage"`); `switchedOffCaptureReturnsNull`. Use Micrometer's `SimpleTracer` or the OTel bridge with an in-memory exporter.
  - `OutboxRelayIT.relayedMessageCarriesTheStoredTrace`: call `relay(new OutgoingMessage(PAYMENT_COMMANDS, msg, "00-4bf9…4736-00f067aa0ba902b7-01"))` outside any span; `readWithTrace` → traceId `4bf92f3577b34da6a3ce929d0e0e4736`.
  - `OutboxRelayIT.switchedOffStartsANewTrace` (`@TestPropertySource carry-trace=false`, nested class): same call → traceId ≠ `4bf9…4736`.
  - `OutboxRelayIT.resubmittedPublicationKeepsTheTrace`: pause the Kafka container (`DockerClient.pauseContainerCmd`), place an order via `OrderService.place` inside a test span, assert a row in `event_publication` with `completion_date is null`; unpause; `await` (≤ 40 s) the `payment.commands` message; its traceId = the test span's trace id.
- [ ] **Step 2:** Run Ordering `verify` → the new tests FAIL (compile: no `TraceCarrier`).
- [ ] **Step 3:** Implement the interfaces above; `Outbox.send` publishes `new OutgoingMessage(topic, message, carrier.capture())`; listeners/handlers call `carrier.tag(message)` first thing.
- [ ] **Step 4:** `verify` of all four modules → green (10 tests included).
- [ ] **Step 5:** Commit `feat(11): outbox relay that keeps the trace (carry-trace switch)`.

### Task 4: Saga timeouts and steps in the trace

**Files:** Modify `ordering/.../db/migration/V1__ordering.sql` (`saga.traceparent varchar(55)`), `saga/Sagas.java`, `orders/OrderService.java`; Test `OrderingIT.java`.

**Interfaces:**
- Consumes: `TraceCarrier` (Task 3), `Probe.readWithTrace` (Task 2).
- Produces: `Sagas.insert(UUID, Saga, Instant deadline, String traceparent)`, `Sagas.update(UUID, Saga, Instant deadline, String traceparent)`, `Sagas.traceparent(UUID) -> Optional<String>`.

- [ ] **Step 1: failing test** `OrderingIT.timeoutCompensationsStayInTheSagaTrace`: orchestration order, answer `PAYMENT_RESERVED`, let inventory time out (step timeout 10 s); `readWithTrace` on `payment.commands` (PROCESS_PAYMENT, REFUND_PAYMENT) and `inventory.commands` → all trace ids equal. Plus `ordersOfOneSagaShareOneTrace` for choreography: `ordering.events` OrderPlaced and the final reply share the trace id of PlaceOrder's HTTP request (send the request with header `traceparent: 00-<random 32 hex>-<16 hex>-01`, assert that trace id on `ordering.events`).
- [ ] **Step 2:** Run → FAIL (timeout runs in the scheduler's trace).
- [ ] **Step 3:** `apply` stores `carrier.capture()` with the saga row and tags `saga.step`; `timeouts()` handles each overdue order inside `carrier.continueTrace(sagas.traceparent(id).orElse(null), "saga timeout", …)`.
- [ ] **Step 4:** Run → PASS; module `verify` green.
- [ ] **Step 5:** Commit `feat(11): saga timeouts continue the saga trace`.

### Task 5: Saga and outbox metrics

**Files:** Create `ordering/.../orders/SagaMetrics.java`, `messaging/OutboxMetrics.java` (Ordering, Payment, Inventory); Modify `OrderService.java`; Test `ordering/.../orders/SagaMetricsIT.java`.

**Interfaces:**
- `SagaMetrics`: `settled(String style, String outcome, String reason, Instant placedAt)` → counter `saga.completed` (`reason` = `none` when null) + timer `saga.duration`; `stepDone(String style, String step, Instant stepStartedAt)` → timer `saga.step.duration`; `timedOut(String step)` → counter `saga.timeouts`. Steps: `PAYMENT_*` → `payment`, `STOCK_*` → `inventory`, `DELIVERY_*` → `delivery`. `placedAt` = first history entry, `stepStartedAt` = latest history entry before this one (`Orders.historyOf`).
- `OutboxMetrics`: gauge `outbox.publications.incomplete` = `select count(*) from event_publication where completion_date is null`.

- [ ] **Step 1: failing test** `SagaMetricsIT`: choreography order for alice that is confirmed (participants simulated as in `OrderingIT`) → `registry.get("saga.completed").tags("style","choreography","outcome","confirmed").counter().count() == 1.0`, `saga.duration` count 1, `saga.step.duration` with `step=delivery` count 1; bob with amount > 50 rejected → `outcome=rejected`; `outbox.publications.incomplete` gauge exists and is ≥ 0.
- [ ] **Step 2:** Run → FAIL. **Step 3:** Implement; call sites in `settle`, `onEvent`, `onReply`, `timeouts`. **Step 4:** PASS, `verify` green.
- [ ] **Step 5:** Commit `feat(11): saga and outbox metrics`.

### Task 6: Delivery with the Java agent

**Files:** Modify `delivery/pom.xml` (maven-dependency-plugin `copy` of `io.opentelemetry.javaagent:opentelemetry-javaagent:2.32.0` to `target/otel/opentelemetry-javaagent.jar` in `prepare-package`; Jib `extraDirectories` `target/otel` → `/otel`; Jib `environment.JAVA_TOOL_OPTIONS` = `-javaagent:/otel/opentelemetry-javaagent.jar -XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError`).

- [ ] **Step 1:** Verification (no unit test — the agent is outside the code): `./mvnw -f 11-observability/pom.xml -pl delivery package jib:dockerBuild -DskipTests` then `docker run --rm --entrypoint ls hephaestus/observability-delivery:jib /otel` → `opentelemetry-javaagent.jar`. Delivery tests unchanged and green (no agent in tests).
- [ ] **Step 2:** Commit `feat(11): Delivery traced by the OpenTelemetry Java agent only`.

### Task 7: Stack, Grafana, e2e

**Files:** Modify `compose.yaml`, `scripts/e2e.sh`; Create `grafana/dashboards/saga.json`, `grafana/provisioning/dashboards/hephaestus.yaml`, `grafana/provisioning/alerting/outbox.yaml`.

- [ ] **Step 1:** Compose: service `lgtm` `grafana/otel-lgtm:0.29.2`, ports from Global Constraints, healthcheck `curl -fs localhost:3000/api/health`, mounts: dashboards → `/otel-lgtm/grafana/conf/provisioning/dashboards/custom/`, provider file and alert rule into `/otel-lgtm/grafana/conf/provisioning/{dashboards,alerting}/`. Boot services: `OTLP_ENDPOINT=http://lgtm:4318`, `OUTBOX_CARRY_TRACE=${OUTBOX_CARRY_TRACE:-true}`. Delivery: `OTEL_SERVICE_NAME=delivery`, `OTEL_EXPORTER_OTLP_ENDPOINT=http://lgtm:4318`, `OTEL_METRIC_EXPORT_INTERVAL=10000`. Services do **not** depend on `lgtm` (they must start without it); `mem_limit` Delivery 640m (agent).
- [ ] **Step 2:** Dashboard «Saga» panels (spec § Grafana) with PromQL on `saga_completed_total`, `saga_duration_seconds_bucket`, `saga_step_duration_seconds_bucket`, `saga_timeouts_total`, `outbox_publications_incomplete`, HTTP 5xx; Tempo table panel `{ resource.service.name = "ordering" }`. Alert rule «Outbox backlog»: `max(outbox_publications_incomplete) > 0` for 1m.
- [ ] **Step 3:** e2e (sed/grep only, as in 10; poll up to 60 s for telemetry):
  - `trace_ids <orderId>`: `GET 127.0.0.1:3200/api/search?q={ span.order.id = "<id>" }&start=…` → unique `traceID`s; `services <traceId>`: `GET /api/traces/<id>` → unique `service.name` values.
  - Scenario 1 (both styles): exactly 1 trace; services = ordering, payment, inventory, delivery.
  - Scenario 2 (bob, both styles): 1 trace; `saga_completed_total{outcome="rejected"}` > 0 via `127.0.0.1:9090/api/v1/query`.
  - Scenario 3: stop Kafka, place an orchestration order, start Kafka, CONFIRMED → still exactly 1 trace.
  - Scenario 4: stop Delivery, orchestration → REJECTED by timeout; the trace contains a span named `saga timeout`; 1 trace.
  - Loki: `GET 127.0.0.1:3100/loki/api/v1/query_range?query={service_name="ordering"} | trace_id="<traceId>"` → ≥ 1 entry.
  - Grafana: `/api/search?query=Saga` (admin:admin) finds the dashboard; `/api/v1/provisioning/alert-rules` contains `Outbox backlog`.
  - Keep `SAGA_STEP_TIMEOUT=60s` as in 10.
- [ ] **Step 4:** Dmitry runs `11-observability/scripts/e2e.sh` → `E2E OK`.
- [ ] **Step 5:** Commit `test(11): Compose with otel-lgtm, Grafana provisioning, trace-checking e2e`.

### Task 8: Docs and CI

**Files:** Create `11-observability/README.md`, `docs/start.md` (scenarios 1–5 and 3a by hand, incl. `OUTBOX_CARRY_TRACE=false` and the alert), `docs/architecture.md`, `docs/learning-notes.md` (Boot vs agent table, outbox and trace context, head vs tail sampling, exemplars, Pyroscope, JDBC tracing, what changes in k8s/12), `.github/workflows/11-observability.yml` (copy of 10's, names/paths/images `hephaestus/observability-*`); Modify root `README.md` (badge, status row).

- [ ] **Step 1:** Write; check links and commands against the code.
- [ ] **Step 2:** Commit `docs(11): README, start, architecture, learning notes; CI`.
