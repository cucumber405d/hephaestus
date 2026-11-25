package de.ibikus.appa.hephaestus.cdc.ordering.ordering.internal;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Debezium reads the WAL, not the table: rows are kept OUTBOX_RETENTION (1 h) only so a person can look at them.
 * The deletes are change events too; the Event Router produces nothing for them.
 */
@Component
@ConditionalOnProperty(name = "outbox.mode", havingValue = "debezium", matchIfMissing = true)
class OutboxCleanup {

    private final JdbcClient jdbc;
    private final Duration retention;

    OutboxCleanup(JdbcClient jdbc, @Value("${outbox.retention}") Duration retention) {
        this.jdbc = jdbc;
        this.retention = retention;
    }

    @Scheduled(fixedDelayString = "${outbox.cleanup-interval}")
    int cleanup() {
        return jdbc.sql(
                        "delete from ordering.outbox where created_at < clock_timestamp() - (? * interval '1 millisecond')")
                .param(retention.toMillis())
                .update();
    }
}
