package de.ibikus.appa.hephaestus.containersci.catalog.domain;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * A non-negative amount with at most ten integer and two fraction digits (the numeric(12,2) column) in an ISO 4217
 * currency. More digits are rejected instead of rounded: silently changing a price is worse than refusing it.
 */
public record Money(BigDecimal amount, String currency) {

    public static final int MAX_INTEGER_DIGITS = 10;

    /** Plain decimal notation only: an exponent like 1E+999999999 would make setScale allocate a huge number. */
    private static final Pattern FORMAT = Pattern.compile("-?\\d{1,20}(\\.\\d{1,20})?");

    public Money {
        if (amount == null) {
            throw new InvalidValueException("Amount is required");
        }
        if (amount.signum() < 0) {
            throw new InvalidValueException("Amount must not be negative");
        }
        // Checked on the unscaled representation before setScale; never call toPlainString on an unchecked value
        var stripped = amount.stripTrailingZeros();
        if (stripped.scale() > 2) {
            throw new InvalidValueException("Amount must have at most 2 fraction digits");
        }
        if (stripped.precision() - stripped.scale() > MAX_INTEGER_DIGITS) {
            throw new InvalidValueException("Amount must have at most " + MAX_INTEGER_DIGITS + " integer digits");
        }
        if (currency == null || !currency.matches("[A-Z]{3}")) {
            throw new InvalidValueException("Currency must be an ISO 4217 code: " + currency);
        }
        amount = amount.setScale(2);
    }

    public static Money of(String amount, String currency) {
        if (amount == null || !FORMAT.matcher(amount).matches()) {
            throw new InvalidValueException("Amount is not a plain decimal number");
        }
        return new Money(new BigDecimal(amount), currency);
    }
}
