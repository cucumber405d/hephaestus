package de.ibikus.appa.hephaestus.security.ordering.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A placed order with the Inventory reservation that holds its stock. customerId is the "sub" claim of the token
 * that placed it: the stable Keycloak user id, not the user name.
 */
public record Order(
        UUID id, String customerId, OrderStatus status, List<OrderLine> lines, UUID reservationId, Instant createdAt) {

    public static final int MAX_LINES = 20;
    public static final int MAX_CUSTOMER_ID_LENGTH = 64;

    public Order {
        Objects.requireNonNull(id, "id");
        requireValidCustomerId(customerId);
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(reservationId, "reservationId");
        Objects.requireNonNull(createdAt, "createdAt");
        requireValidLines(lines);
        lines = List.copyOf(lines);
    }

    public static void requireValidCustomerId(String customerId) {
        if (customerId == null || customerId.isBlank() || customerId.length() > MAX_CUSTOMER_ID_LENGTH) {
            throw new InvalidValueException(
                    "An order needs a customer id of 1.." + MAX_CUSTOMER_ID_LENGTH + " characters");
        }
    }

    public static void requireValidLines(List<OrderLine> lines) {
        if (lines == null || lines.isEmpty() || lines.size() > MAX_LINES) {
            throw new InvalidValueException("An order needs 1.." + MAX_LINES + " lines");
        }
    }
}
