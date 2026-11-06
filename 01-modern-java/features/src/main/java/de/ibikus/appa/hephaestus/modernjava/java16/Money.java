package de.ibikus.appa.hephaestus.modernjava.java16;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Java 16 records (JEP 395; preview in 14 and 15): a compact constructor validates and normalizes the
 * components; equals, hashCode and toString are generated from them.
 *
 * <p>Usage:
 * {@snippet :
 * Money total = Money.eur("12.50").plus(Money.eur("3.20")); // @highlight substring="plus"
 * }
 */
public record Money(BigDecimal amount, String currency) {

    public Money {
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("Amount must not be negative: " + amount);
        }
        if (currency == null || !currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Currency must be an ISO 4217 code: " + currency);
        }
        amount = amount.setScale(2, RoundingMode.HALF_UP);
    }

    public static Money of(String amount, String currency) {
        return new Money(new BigDecimal(amount), currency);
    }

    public static Money eur(String amount) {
        return of(amount, "EUR");
    }

    public Money plus(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("Cannot add " + other.currency + " to " + currency);
        }
        return new Money(amount.add(other.amount), currency);
    }

    public Money times(int factor) {
        return new Money(amount.multiply(BigDecimal.valueOf(factor)), currency);
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency;
    }
}
