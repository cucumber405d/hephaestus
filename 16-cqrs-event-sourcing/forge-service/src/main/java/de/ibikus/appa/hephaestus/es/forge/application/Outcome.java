package de.ibikus.appa.hephaestus.es.forge.application;

import java.util.UUID;

/**
 * What Ordering learns about a work order: completed or failed. The event id is derived from the journal row (stream
 * + version), so a republished outcome carries the same id and the consumer's inbox drops the duplicate.
 */
public record Outcome(UUID eventId, Kind kind, UUID orderId, String workOrderId, String reason) {

    public enum Kind {
        COMPLETED,
        FAILED
    }
}
