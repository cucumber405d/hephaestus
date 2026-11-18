package de.ibikus.appa.hephaestus.observability.ordering.orders;

import static de.ibikus.appa.hephaestus.observability.ordering.messaging.SagaMessage.*;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * The saga in numbers (the dashboard's domain panels): how orders end per style, how long a saga and each step take,
 * how often a step times out. Tags stay low-cardinality: style, outcome, the participants' fixed reasons, step.
 */
@Component
public class SagaMetrics {

    private final MeterRegistry registry;

    public SagaMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** The order reached CONFIRMED or REJECTED. */
    public void settled(String style, String outcome, String reason, Instant placedAt) {
        Counter.builder("saga.completed")
                .description("Orders whose saga ended")
                .tag("style", style)
                .tag("outcome", outcome.toLowerCase(Locale.ROOT))
                .tag("reason", reason == null ? "none" : reason)
                .register(registry)
                .increment();
        Timer.builder("saga.duration")
                .description("From placing the order to its final status")
                .tag("style", style)
                .publishPercentileHistogram()
                .register(registry)
                .record(Duration.between(placedAt, Instant.now()));
    }

    /** A participant answered for its step (reply or event); compensations are not steps. */
    public void stepDone(String style, String messageType, Instant stepStartedAt) {
        var step = stepOf(messageType);
        if (step == null) {
            return;
        }
        Timer.builder("saga.step.duration")
                .description("From the previous saga entry to the participant's answer")
                .tag("style", style)
                .tag("step", step)
                .publishPercentileHistogram()
                .register(registry)
                .record(Duration.between(stepStartedAt, Instant.now()));
    }

    public void timedOut(String step) {
        Counter.builder("saga.timeouts")
                .description("Orchestration steps that did not answer in time")
                .tag("step", step)
                .register(registry)
                .increment();
    }

    static String stepOf(String messageType) {
        return switch (messageType) {
            case PAYMENT_RESERVED, PAYMENT_DECLINED -> "payment";
            case STOCK_RESERVED, STOCK_UNAVAILABLE -> "inventory";
            case DELIVERY_SCHEDULED, DELIVERY_FAILED -> "delivery";
            default -> null;
        };
    }
}
