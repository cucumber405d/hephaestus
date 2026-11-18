package de.ibikus.appa.hephaestus.resilience.inventory.application.port.in;

import java.util.Locale;

/** The three ways of protecting the supplier call that this subproject compares. */
public enum Mechanism {
    /** Resilience4j used directly: rate limiter, circuit breaker, retry, bulkhead. */
    R4J,
    /** Spring Cloud Circuit Breaker abstraction (Resilience4j implementation): circuit breaker and time limiter. */
    SCCB,
    /** Spring Framework 7 built-in @Retryable and @ConcurrencyLimit: no circuit breaker. */
    SPRING;

    /** Path value, e.g. "r4j". */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Mechanism fromId(String id) {
        for (var mechanism : values()) {
            if (mechanism.id().equals(id)) {
                return mechanism;
            }
        }
        throw new IllegalArgumentException("Unknown mechanism " + id + "; use r4j, sccb or spring");
    }
}
