package de.ibikus.appa.hephaestus.containersci.catalog.domain;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * UUID version 7 (RFC 9562) on the plain JDK: 48 bits of Unix milliseconds, then randomness. Ids created later sort
 * later, so B-tree indexes grow at the right edge instead of being written all over like with random v4 UUIDs.
 */
public final class Ids {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Ids() {}

    public static UUID uuidV7() {
        long millis = System.currentTimeMillis();
        long mostSignificant = (millis << 16) | 0x7000L | (RANDOM.nextInt() & 0x0FFFL);
        long leastSignificant = (RANDOM.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL) | 0x8000_0000_0000_0000L;
        return new UUID(mostSignificant, leastSignificant);
    }
}
