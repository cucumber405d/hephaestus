package de.ibikus.appa.hephaestus.modernjava.java09;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Java 9 private interface methods: shared helper logic for default methods without exposing it. */
public interface PriceFormatter {

    default String format(BigDecimal amount) {
        return roundToCents(amount).toPlainString() + " " + currency();
    }

    default String currency() {
        return "EUR";
    }

    private BigDecimal roundToCents(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
