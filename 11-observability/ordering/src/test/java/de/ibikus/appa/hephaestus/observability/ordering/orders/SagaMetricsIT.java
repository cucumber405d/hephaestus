package de.ibikus.appa.hephaestus.observability.ordering.orders;

import static de.ibikus.appa.hephaestus.observability.ordering.messaging.SagaMessage.*;
import static org.assertj.core.api.Assertions.assertThat;

import de.ibikus.appa.hephaestus.observability.ordering.Containers;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** The saga metrics, the participants' events handed to the service directly (no Kafka round trip). */
@SpringBootTest
@Import(Containers.class)
class SagaMetricsIT {

    @Autowired
    OrderService service;

    @Autowired
    MeterRegistry registry;

    @Test
    void confirmedChoreographyIsCountedWithItsDurations() {
        var before = completed("choreography", "confirmed", "none");
        var stepsBefore = timerCount("saga.step.duration", "style", "choreography", "step", "delivery");
        var order = service.place("alice", CHOREOGRAPHY, List.of(new Line("HAMMER-01", 1)));

        for (var type : List.of(PAYMENT_RESERVED, STOCK_RESERVED, DELIVERY_SCHEDULED)) {
            service.onEvent(Orders.message(order, type, null));
        }

        assertThat(completed("choreography", "confirmed", "none")).isEqualTo(before + 1);
        assertThat(timerCount("saga.step.duration", "style", "choreography", "step", "delivery"))
                .isEqualTo(stepsBefore + 1);
        assertThat(timerCount("saga.duration", "style", "choreography")).isPositive();
    }

    @Test
    void declinedOrderIsCountedAsRejectedWithItsReason() {
        var before = completed("choreography", "rejected", "credit limit exceeded");
        var order = service.place("bob", CHOREOGRAPHY, List.of(new Line("ANVIL-01", 1)));

        service.onEvent(Orders.message(order, PAYMENT_DECLINED, "credit limit exceeded"));

        assertThat(completed("choreography", "rejected", "credit limit exceeded"))
                .isEqualTo(before + 1);
    }

    @Test
    void outboxBacklogIsAGauge() {
        assertThat(registry.get("outbox.publications.incomplete").gauge().value())
                .isGreaterThanOrEqualTo(0);
    }

    private double completed(String style, String outcome, String reason) {
        Counter counter = registry.find("saga.completed")
                .tags("style", style, "outcome", outcome, "reason", reason)
                .counter();
        return counter == null ? 0 : counter.count();
    }

    private long timerCount(String name, String... tags) {
        Timer timer = registry.find(name).tags(tags).timer();
        return timer == null ? 0 : timer.count();
    }
}
