package de.ibikus.appa.hephaestus.observability.ordering.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelPropagator;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.Test;

/** The trace carrier on the real OpenTelemetry bridge, without Spring. */
class TraceCarrierTest {

    static final String PARENT = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

    final io.opentelemetry.api.trace.Tracer otel =
            SdkTracerProvider.builder().build().get("test");
    final Tracer tracer = new OtelTracer(otel, new OtelCurrentTraceContext(), event -> {});
    final OtelPropagator propagator =
            new OtelPropagator(ContextPropagators.create(W3CTraceContextPropagator.getInstance()), otel);

    @Test
    void continueTraceWithValidParentUsesItsTraceId() {
        var carrier = new TraceCarrier(tracer, propagator, true);

        var traceId = carrier.continueTrace(
                PARENT, "outbox relay", () -> tracer.currentSpan().context().traceId());

        assertThat(traceId).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(tracer.currentSpan()).isNull();
    }

    @Test
    void nullOrMalformedParentRunsWorkWithoutException() {
        var carrier = new TraceCarrier(tracer, propagator, true);

        assertThat(carrier.continueTrace(null, "outbox relay", () -> "done")).isEqualTo("done");
        assertThat(carrier.continueTrace("garbage", "outbox relay", () -> tracer.currentSpan()))
                .isNull();
    }

    @Test
    void captureReturnsTheCurrentTraceAndNothingWhenSwitchedOff() {
        var on = new TraceCarrier(tracer, propagator, true);
        var off = new TraceCarrier(tracer, propagator, false);
        var span = tracer.nextSpan().name("business").start();
        try (var scope = tracer.withSpan(span)) {
            assertThat(on.capture()).startsWith("00-" + span.context().traceId() + "-");
            assertThat(off.capture()).isNull();
        } finally {
            span.end();
        }
        assertThat(on.capture()).isNull();
    }

    @Test
    void switchedOffIgnoresTheStoredParent() {
        var off = new TraceCarrier(tracer, propagator, false);

        assertThat(off.continueTrace(PARENT, "outbox relay", () -> tracer.currentSpan())).isNull();
    }
}
