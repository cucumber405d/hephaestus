package de.ibikus.appa.hephaestus.eventskafka.ordering.adapter.out.messaging;

import de.ibikus.appa.hephaestus.eventskafka.ordering.domain.Order;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The published contract (JSON on topic orders.events, header eventType=OrderPlaced). Consumers keep their own copy
 * of this shape; adding fields is compatible, renaming or removing is not.
 */
public record OrderPlacedEvent(UUID eventId, UUID orderId, String customerId, List<Line> lines, Instant occurredAt) {

    public record Line(String sku, int quantity) {}

    static OrderPlacedEvent of(Order order) {
        return new OrderPlacedEvent(
                order.eventId(),
                order.id(),
                order.customerId(),
                order.lines().stream()
                        .map(line -> new Line(line.sku(), line.quantity()))
                        .toList(),
                order.createdAt());
    }
}
