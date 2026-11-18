package de.ibikus.appa.hephaestus.observability.inventory.messaging;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Carries the trace across the outbox and the scheduler: {@link #capture()} turns the current span into a W3C
 * {@code traceparent} stored with the message (or the saga), {@link #continueTrace} makes it the parent again
 * later, in another thread, minutes later if Kafka was down. Switched off by {@code observability.outbox.carry-trace}
 * to see the trace break.
 */
@Component
public class TraceCarrier {

    private static final Pattern TRACEPARENT = Pattern.compile("00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}");

    private final Tracer tracer;
    private final Propagator propagator;
    private final boolean enabled;

    public TraceCarrier(
            Tracer tracer, Propagator propagator, @Value("${observability.outbox.carry-trace:true}") boolean enabled) {
        this.tracer = tracer;
        this.propagator = propagator;
        this.enabled = enabled;
    }

    /** The current trace as a W3C traceparent, or {@code null} (switched off, or no current span). */
    public String capture() {
        if (!enabled) {
            return null;
        }
        var context = tracer.currentTraceContext().context();
        if (context == null) {
            return null;
        }
        var carrier = new HashMap<String, String>();
        propagator.inject(context, carrier, Map::put);
        return carrier.get("traceparent");
    }

    /**
     * Runs the work in a new span whose parent is the stored traceparent. Switched off, without or with an unreadable
     * traceparent the work runs as it is, in whatever trace the current thread has (possibly none).
     */
    public <T> T continueTrace(String traceparent, String spanName, Supplier<T> work) {
        if (!enabled || traceparent == null || !TRACEPARENT.matcher(traceparent).matches()) {
            return work.get();
        }
        Span span = propagator
                .extract(Map.of("traceparent", traceparent), Map::get)
                .name(spanName)
                .start();
        try (var scope = tracer.withSpan(span)) {
            return work.get();
        } catch (RuntimeException e) {
            span.error(e);
            throw e;
        } finally {
            span.end();
        }
    }

    /** Business attributes on the current span: what Tempo searches by ({@code { span.order.id = "..." }}). */
    public void tag(SagaMessage message) {
        tag("order.id", message.orderId().toString());
        tag("saga.style", message.saga());
        tag("message.type", message.type());
    }

    public void tag(String key, String value) {
        var span = tracer.currentSpan();
        if (span != null && value != null) {
            span.tag(key, value);
        }
    }
}
