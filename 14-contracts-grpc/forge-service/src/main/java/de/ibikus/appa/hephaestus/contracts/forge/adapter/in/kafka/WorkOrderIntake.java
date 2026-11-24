package de.ibikus.appa.hephaestus.contracts.forge.adapter.in.kafka;

import de.ibikus.appa.hephaestus.contracts.events.WorkOrderRequest;
import de.ibikus.appa.hephaestus.contracts.forge.application.QuoteRef;
import de.ibikus.appa.hephaestus.contracts.forge.application.WorkOrders;
import java.util.Optional;
import java.util.UUID;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * New work orders from Ordering ({@code WorkOrderRequest} on forge.requests, Avro through Apicurio). Kafka delivers
 * at least once; the store creates one work order per customer order, so a redelivery changes nothing. A request
 * without a quote was written by a 1.0.0 producer: accepted as before (BACKWARD, the consumer is upgraded first).
 */
@Component
class WorkOrderIntake {

    private final WorkOrders workOrders;

    WorkOrderIntake(WorkOrders workOrders) {
        this.workOrders = workOrders;
    }

    @KafkaListener(topics = "forge.requests", groupId = "forge-service")
    void on(WorkOrderRequest request) {
        var quote = request.getQuoteId() == null
                ? Optional.<QuoteRef>empty()
                : Optional.of(new QuoteRef(
                        request.getQuoteId(), request.getPriceCents() == null ? -1 : request.getPriceCents()));
        // a malformed id is an IllegalArgumentException: not retried, parked on the DLT (KafkaErrorHandling)
        workOrders.request(UUID.fromString(request.getOrderId()), request.getMaterial(), request.getQuantity(), quote);
    }
}
