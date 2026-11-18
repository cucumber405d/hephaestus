package de.ibikus.appa.hephaestus.outboxsaga.payment.admin;

import java.util.Set;

/** Experiment levers: let this step fail for some customers, or slow every message down. */
public record Faults(Set<String> failCustomers, long delayMs) {

    public static final Faults NONE = new Faults(Set.of(), 0);

    public Faults {
        failCustomers = failCustomers == null ? Set.of() : Set.copyOf(failCustomers);
        if (delayMs < 0 || delayMs > 10_000) {
            throw new IllegalArgumentException("delayMs within 0..10000");
        }
    }
}
