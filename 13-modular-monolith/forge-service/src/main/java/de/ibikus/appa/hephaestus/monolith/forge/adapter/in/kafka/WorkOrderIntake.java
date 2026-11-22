package de.ibikus.appa.hephaestus.monolith.forge.adapter.in.kafka;

import de.ibikus.appa.hephaestus.monolith.forge.application.WorkOrders;
import java.util.UUID;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * New work orders from the monolith after the cutover ({@code RemoteWorkOrderRequested} on forge.requests). Kafka
 * delivers at least once; the store creates one work order per customer order, so a redelivery changes nothing.
 * The payload is converted by Spring Modulith's Kafka JSON support (enable-json also sets the listener converter).
 */
@Component
class WorkOrderIntake {

    record RemoteWorkOrderRequested(UUID orderId, String material, Integer quantity) {}

    private final WorkOrders workOrders;

    WorkOrderIntake(WorkOrders workOrders) {
        this.workOrders = workOrders;
    }

    @KafkaListener(topics = "forge.requests", groupId = "forge-service")
    void on(RemoteWorkOrderRequested request) {
        workOrders.request(request.orderId(), request.material(), request.quantity() == null ? 1 : request.quantity());
    }
}
