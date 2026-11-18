package de.ibikus.appa.hephaestus.eventskafka.notification.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Notification's own copy of the OrderPlaced contract (no shared model library with Ordering). Validated on
 * creation: an event that breaks the contract is a poison message, not something a retry could fix.
 */
public record OrderPlaced(UUID eventId, UUID orderId, String customerId, List<Line> lines, Instant occurredAt) {

    public record Line(String sku, int quantity) {}

    public OrderPlaced {
        if (eventId == null || orderId == null || customerId == null || customerId.isBlank()) {
            throw new InvalidEventException("OrderPlaced needs eventId, orderId and customerId");
        }
        if (lines == null || lines.isEmpty()) {
            throw new InvalidEventException("OrderPlaced " + eventId + " has no lines");
        }
        lines = List.copyOf(lines);
    }

    public int itemCount() {
        return lines.stream().mapToInt(Line::quantity).sum();
    }
}
