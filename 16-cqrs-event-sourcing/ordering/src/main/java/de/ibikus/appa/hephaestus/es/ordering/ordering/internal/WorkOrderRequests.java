package de.ibikus.appa.hephaestus.es.ordering.ordering.internal;

import de.ibikus.appa.hephaestus.contracts.events.WorkOrderRequest;

/** The one mapping to the Avro request (contract 1.1.0), shared by both delivery modes. */
final class WorkOrderRequests {

    /** The topic, also the Apicurio artifact the serializer resolves ({@code <topic>-value}). */
    static final String TOPIC = "es.forge.requests";

    private WorkOrderRequests() {}

    static WorkOrderRequest toAvro(WorkOrderRequestedEvent event) {
        return WorkOrderRequest.newBuilder()
                .setOrderId(event.orderId().toString())
                .setMaterial(event.material())
                .setQuantity(event.quantity())
                .setQuoteId(event.quoteId())
                .setPriceCents(event.priceCents())
                .build();
    }
}
