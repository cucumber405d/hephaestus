package de.ibikus.appa.hephaestus.resilience.inventory.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Units held for one order; at most one reservation per order. */
public record Reservation(UUID id, UUID orderId, List<ReservationLine> lines, Instant createdAt) {

    public static final int MAX_LINES = 20;

    public Reservation {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(createdAt, "createdAt");
        if (lines == null || lines.isEmpty() || lines.size() > MAX_LINES) {
            throw new InvalidValueException("A reservation needs 1.." + MAX_LINES + " lines");
        }
        lines = List.copyOf(lines);
    }
}
