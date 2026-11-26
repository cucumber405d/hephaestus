package de.ibikus.appa.hephaestus.es.forge.adapter.out.events;

import java.util.UUID;

/**
 * The outcome inside the service: published in the work order's transaction and kept by the Spring Modulith event
 * publication registry (the outbox) until {@link OutcomeRelay} has handed it to Kafka. A plain record on purpose —
 * the registry stores it as JSON; the Avro {@code ForgeEvent} exists only on the wire.
 */
record WorkOrderOutcome(UUID eventId, Kind kind, UUID orderId, String workOrderId, String reason) {

    enum Kind {
        COMPLETED,
        FAILED
    }
}
