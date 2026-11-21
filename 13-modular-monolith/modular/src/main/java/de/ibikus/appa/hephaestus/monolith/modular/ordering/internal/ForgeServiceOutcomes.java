package de.ibikus.appa.hephaestus.monolith.modular.ordering.internal;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Outcomes of forge-service's work orders, from Kafka: handled exactly like the local forge's events. Kafka delivers
 * at least once, so every {@code eventId} is applied once (inbox table), and in the same transaction as its effect.
 */
@Component
class ForgeServiceOutcomes {

    /** {@code type}: WorkOrderCompleted or WorkOrderFailed. */
    record ForgeEventMessage(UUID eventId, String type, UUID orderId, String workOrderId, String reason) {}

    private final OrderService orders;
    private final JdbcClient jdbc;
    private final JsonMapper json;

    ForgeServiceOutcomes(OrderService orders, JdbcClient jdbc, JsonMapper json) {
        this.orders = orders;
        this.jdbc = jdbc;
        this.json = json;
    }

    @KafkaListener(topics = "forge.events", groupId = "monolith")
    @Transactional
    void on(String message) {
        var event = json.readValue(message, ForgeEventMessage.class);
        var first = jdbc.sql("insert into ordering.processed_event (event_id) values (?) on conflict do nothing")
                        .param(event.eventId())
                        .update()
                == 1;
        if (!first) {
            return;
        }
        switch (event.type()) {
            case "WorkOrderCompleted" -> orders.markReady(event.orderId());
            case "WorkOrderFailed" -> orders.reject(event.orderId(), event.reason());
            default -> throw new IllegalArgumentException("unknown forge event type: " + event.type());
        }
    }
}
