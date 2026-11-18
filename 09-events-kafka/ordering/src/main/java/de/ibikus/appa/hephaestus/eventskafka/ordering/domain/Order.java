package de.ibikus.appa.hephaestus.eventskafka.ordering.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A placed order with the reservation that holds its stock. {@code customerId} keys its events (all events of one
 * customer land in one partition, in order); {@code eventId} identifies its OrderPlaced event, so a re-sent event
 * is recognisably the same one.
 */
public record Order(
        UUID id,
        String customerId,
        UUID eventId,
        OrderStatus status,
        List<OrderLine> lines,
        UUID reservationId,
        Instant createdAt) {

    public static final int MAX_LINES = 20;

    public static final String CUSTOMER_ID_PATTERN = "[A-Za-z0-9-]{1,64}";

    public Order {
        Objects.requireNonNull(id, "id");
        if (customerId == null || !customerId.matches(CUSTOMER_ID_PATTERN)) {
            throw new InvalidValueException("customerId must match " + CUSTOMER_ID_PATTERN);
        }
        Objects.requireNonNull(eventId, "eventId");
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
