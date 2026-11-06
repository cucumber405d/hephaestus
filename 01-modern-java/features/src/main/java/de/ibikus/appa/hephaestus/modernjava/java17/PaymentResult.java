package de.ibikus.appa.hephaestus.modernjava.java17;

import de.ibikus.appa.hephaestus.modernjava.java16.Money;
import java.time.Duration;
import java.util.HexFormat;
import java.util.random.RandomGenerator;

/**
 * Java 17 sealed types (JEP 409; preview in 15 and 16): the payment provider can answer in exactly three ways,
 * and the compiler knows it. Also {@code RandomGenerator} (JEP 356) and {@code HexFormat}.
 */
public sealed interface PaymentResult {

    record Approved(String transactionId, Money amount) implements PaymentResult {}

    record Declined(String reason) implements PaymentResult {}

    record Pending(Duration retryAfter) implements PaymentResult {}

    /** 8 random bytes as 16 lower-case hex characters. */
    static String newTransactionId(RandomGenerator random) {
        var bytes = new byte[8];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) random.nextInt(256);
        }
        return HexFormat.of().formatHex(bytes);
    }
}
