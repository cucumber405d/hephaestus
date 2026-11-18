package de.ibikus.appa.hephaestus.outboxsaga.delivery.deliveries;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Delivery rules: one delivery per order; a cancelled order is never scheduled afterwards. */
@Component
public class Deliveries {

    public enum Outcome {
        SCHEDULED,
        FAILED
    }

    private final JdbcClient jdbc;

    public Deliveries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Outcome schedule(UUID orderId, String customerId, boolean courierAvailable) {
        var previous = status(orderId);
        if (previous.isPresent()) {
            return "SCHEDULED".equals(previous.get()) ? Outcome.SCHEDULED : Outcome.FAILED;
        }
        var outcome = courierAvailable ? Outcome.SCHEDULED : Outcome.FAILED;
        record(orderId, customerId, outcome.name());
        return outcome;
    }

    /** Cancels a scheduled delivery, or remembers the cancellation if the schedule has not arrived yet. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void cancel(UUID orderId, String customerId) {
        if (status(orderId).isEmpty()) {
            record(orderId, customerId, "CANCELLED");
            return;
        }
        jdbc.sql("update delivery set status = 'CANCELLED', updated_at = ? where order_id = ? and status = 'SCHEDULED'")
                .params(Timestamp.from(Instant.now()), orderId)
                .update();
    }

    public Optional<String> status(UUID orderId) {
        return jdbc.sql("select status from delivery where order_id = ?")
                .param(orderId)
                .query(String.class)
                .optional();
    }

    private void record(UUID orderId, String customerId, String status) {
        jdbc.sql("insert into delivery (order_id, customer_id, status, updated_at) values (?, ?, ?, ?)")
                .params(orderId, customerId, status, Timestamp.from(Instant.now()))
                .update();
    }
}
