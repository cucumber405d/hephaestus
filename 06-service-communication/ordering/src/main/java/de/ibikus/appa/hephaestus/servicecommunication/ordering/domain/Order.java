package de.ibikus.appa.hephaestus.servicecommunication.ordering.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** A placed order with the Inventory reservation that holds its stock. */
public record Order(UUID id, OrderStatus status, List<OrderLine> lines, UUID reservationId, Instant createdAt) {

    public static final int MAX_LINES = 20;

    public Order {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(reservationId, "reservationId");
        Objects.requireNonNull(createdAt, "createdAt");
        requireValidLines(lines);
        lines = List.copyOf(lines);
    }

    public static void requireValidLines(List<OrderLine> lines) {
        if (lines == null || lines.isEmpty() || lines.size() > MAX_LINES) {
            throw new InvalidValueException("An order needs 1.." + MAX_LINES + " lines");
        }
    }
}
