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
        // At most 10 s: longer pauses run into max.poll.interval.ms (classic) or the record lock (share group) and
        // would cause rebalances or redeliveries that hide what the experiment is about
        if (transientFailures < 0 || delayMs < 0 || delayMs > 10_000) {
            throw new IllegalArgumentException("transientFailures >= 0, delayMs within 0..10000");
        }
    }
}
