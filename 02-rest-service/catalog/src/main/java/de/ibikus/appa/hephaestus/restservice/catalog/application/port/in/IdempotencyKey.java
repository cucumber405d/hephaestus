package de.ibikus.appa.hephaestus.restservice.catalog.application.port.in;

import de.ibikus.appa.hephaestus.restservice.catalog.domain.InvalidValueException;

/** The client's Idempotency-Key header value (1..100 characters). */
public record IdempotencyKey(String value) {

    public static final int MAX_LENGTH = 100;

    public IdempotencyKey {
        if (value == null || value.isBlank() || value.length() > MAX_LENGTH) {
            throw new InvalidValueException("Idempotency-Key must have 1.." + MAX_LENGTH + " characters");
        }
    }
}
