package de.ibikus.appa.hephaestus.observability.payment.messaging;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * The outbox backlog as a gauge: publications not sent yet. Above zero for long means Kafka is unreachable (the
 * Grafana alert "Outbox backlog"); read on every export, NaN while the database is unreachable.
 */
@Component
class OutboxMetrics {

    OutboxMetrics(MeterRegistry registry, JdbcClient jdbc) {
        Gauge.builder("outbox.publications.incomplete", jdbc, OutboxMetrics::incomplete)
                .description("Outbox messages not yet sent to Kafka")
                .strongReference(true)
                .register(registry);
    }

    static double incomplete(JdbcClient jdbc) {
        try {
            return jdbc.sql("select count(*) from event_publication where completion_date is null")
                    .query(Long.class)
                    .single();
        } catch (RuntimeException e) {
            return Double.NaN;
        }
    }
}
