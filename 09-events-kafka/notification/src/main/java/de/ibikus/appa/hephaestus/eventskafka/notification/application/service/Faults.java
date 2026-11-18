package de.ibikus.appa.hephaestus.eventskafka.notification.application.service;

import java.util.Set;

/**
 * Switchable misbehaviour for experiments: always fail for some customers (→ retries, then DLT), fail the next n
 * attempts (→ retries, then success), or slow every event down (→ consumer lag).
 */
public record Faults(Set<String> failCustomers, int transientFailures, long delayMs) {

    public static final Faults NONE = new Faults(Set.of(), 0, 0);

    public Faults {
        failCustomers = failCustomers == null ? Set.of() : Set.copyOf(failCustomers);
        if (transientFailures < 0 || delayMs < 0 || delayMs > 60_000) {
            throw new IllegalArgumentException("transientFailures >= 0, delayMs within 0..60000");
        }
    }
}
