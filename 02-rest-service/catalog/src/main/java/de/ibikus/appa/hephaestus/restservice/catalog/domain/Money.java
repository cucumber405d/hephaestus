package de.ibikus.appa.hephaestus.restservice.catalog.domain;

import java.math.BigDecimal;

/**
 * A non-negative amount with at most two fraction digits in an ISO 4217 currency. More digits are rejected instead
 * of rounded: silently changing a price is worse than refusing it.
 */
public record Money(BigDecimal amount, String currency) {

    public Money {
        if (amount == null) {
            throw new InvalidValueException("Amount is required");
        }
        if (amount.signum() < 0) {
            throw new InvalidValueException("Amount must not be negative: " + amount.toPlainString());
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new InvalidValueException("Amount must have at most 2 fraction digits: " + amount.toPlainString());
        }
        if (currency == null || !currency.matches("[A-Z]{3}")) {
            throw new InvalidValueException("Currency must be an ISO 4217 code: " + currency);
        }
        amount = amount.setScale(2);
    }

    public static Money of(String amount, String currency) {
        try {
            return new Money(new BigDecimal(amount), currency);
        } catch (NumberFormatException | NullPointerException e) {
            throw new InvalidValueException("Amount is not a decimal number: " + amount);
        }
    }
}
