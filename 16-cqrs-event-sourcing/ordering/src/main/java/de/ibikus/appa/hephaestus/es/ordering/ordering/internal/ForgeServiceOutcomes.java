package de.ibikus.appa.hephaestus.es.ordering.ordering.internal;

import de.ibikus.appa.hephaestus.contracts.events.ForgeEvent;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outcomes of forge-service's work orders ({@code ForgeEvent}, Avro). Kafka delivers at least once, so every
 * {@code eventId} is applied once (inbox table), in the same transaction as its effect. Unreadable bytes never get
 * here: the ErrorHandlingDeserializer hands them to the error handler, which parks them on es.forge.events.DLT.
 */
@Component
class ForgeServiceOutcomes {

    private final OrderService orders;
    private final JdbcClient jdbc;

    ForgeServiceOutcomes(OrderService orders, JdbcClient jdbc) {
        this.orders = orders;
        this.jdbc = jdbc;
    }

    @KafkaListener(topics = "es.forge.events", groupId = "ordering")
    @Transactional
    void on(ForgeEvent event) {
        var first = jdbc.sql("insert into ordering.processed_event (event_id) values (?) on conflict do nothing")
                        .param(UUID.fromString(event.getEventId()))
                        .update()
                == 1;
        if (!first) {
            return;
        }
        var orderId = UUID.fromString(event.getOrderId());
        switch (event.getType()) {
            case WORK_ORDER_COMPLETED -> orders.markReady(orderId);
            case WORK_ORDER_FAILED -> orders.reject(orderId, event.getReason());
        }
    }
}
