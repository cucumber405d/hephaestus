package de.ibikus.appa.hephaestus.es.forge.adapter.in.kafka;

import de.ibikus.appa.hephaestus.es.forge.application.eventstore.ConcurrencyConflict;
import de.ibikus.appa.hephaestus.contracts.events.WorkOrderRequest;
import de.ibikus.appa.hephaestus.es.forge.application.QuoteRef;
import de.ibikus.appa.hephaestus.es.forge.application.WorkOrders;
import java.util.Optional;
import java.util.UUID;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * New work orders from Ordering ({@code WorkOrderRequest} on es.forge.requests, Avro through Apicurio). Kafka delivers
 * at least once; the stream id follows from the customer order, so a redelivery appends nothing. A request
 * without a quote was written by a 1.0.0 producer: accepted as before (BACKWARD, the consumer is upgraded first).
 */
@Component
class WorkOrderIntake {

    private static final int CONFLICT_ATTEMPTS = 3;

    private final WorkOrders workOrders;

    WorkOrderIntake(WorkOrders workOrders) {
        this.workOrders = workOrders;
    }

    @KafkaListener(topics = "es.forge.requests", groupId = "forge-service")
    void on(WorkOrderRequest request) {
        var quote = request.getQuoteId() == null
                ? Optional.<QuoteRef>empty()
                : Optional.of(new QuoteRef(
                        request.getQuoteId(), request.getPriceCents() == null ? -1 : request.getPriceCents()));
        // a malformed id is an IllegalArgumentException: not retried, parked on the DLT (KafkaErrorHandling)
        var orderId = UUID.fromString(request.getOrderId());
        for (int attempt = 1; ; attempt++) {
            try {
                workOrders.request(orderId, request.getMaterial(), request.getQuantity(), quote);
                return;
            } catch (ConcurrencyConflict e) {
                // another work order took the same material stream meanwhile: decide again on the new state
                if (attempt == CONFLICT_ATTEMPTS) {
                    throw e; // Kafka's error handling from here (back-off, then the DLT)
                }
            }
        }
    }
}
