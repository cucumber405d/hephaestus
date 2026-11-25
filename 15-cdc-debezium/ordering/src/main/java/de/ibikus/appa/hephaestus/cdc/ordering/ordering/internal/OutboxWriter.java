package de.ibikus.appa.hephaestus.cdc.ordering.ordering.internal;

import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Delivery mode "debezium" (default): a synchronous listener, so the outbox row is part of the order's transaction —
 * both or neither. Nothing is sent from here; Debezium reads the row from the WAL and publishes it.
 */
@Component
@ConditionalOnProperty(name = "outbox.mode", havingValue = "debezium", matchIfMissing = true)
class OutboxWriter {

    private final JdbcClient jdbc;
    private final OutboxSerializer serializer;

    OutboxWriter(JdbcClient jdbc, OutboxSerializer serializer) {
        this.jdbc = jdbc;
        this.serializer = serializer;
    }

    @EventListener
    void on(WorkOrderRequestedEvent event) {
        var payload = serializer.serialize(WorkOrderRequests.toAvro(event));
        jdbc.sql("""
                        insert into ordering.outbox (id, aggregate_type, aggregate_id, type, payload, created_at)
                        values (?, 'forge.requests', ?, 'WorkOrderRequest', ?, clock_timestamp())""")
                .params(UUID.randomUUID(), event.orderId().toString(), payload)
                .update();
    }
}
