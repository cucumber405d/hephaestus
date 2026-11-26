package de.ibikus.appa.hephaestus.es.ordering.ordering.internal;

import java.util.UUID;

/**
 * Inside Ordering: a quoted custom order was accepted. Kept by the event publication registry (the outbox) until
 * {@link RequestRelay} has handed the Avro {@code WorkOrderRequest} to Kafka.
 */
record WorkOrderRequestedEvent(UUID orderId, String material, int quantity, String quoteId, long priceCents) {}
