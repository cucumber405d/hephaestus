package de.ibikus.appa.hephaestus.resilience.supplier;

import java.util.concurrent.ThreadLocalRandom;

/**
 * HTTP-level misbehaviour the network proxy cannot produce: error answers with a given probability and a delay
 * before every answer.
 *
 * @param errorRate probability 0..1 that an offer request is answered with {@code status}
 * @param status HTTP status of the error answers (5xx)
 * @param delayMs pause before every offer answer
 */
public record Faults(double errorRate, int status, long delayMs) {

    public static final Faults NONE = new Faults(0, 503, 0);

    public Faults {
        if (errorRate < 0 || errorRate > 1) {
            throw new IllegalArgumentException("errorRate must be within 0..1");
        }
        if (status < 500 || status > 599) {
            throw new IllegalArgumentException("status must be a 5xx code");
        }
        if (delayMs < 0 || delayMs > 60_000) {
            throw new IllegalArgumentException("delayMs must be within 0..60000");
        }
    }

    boolean failsNow() {
        return errorRate > 0 && ThreadLocalRandom.current().nextDouble() < errorRate;
    }
}
