package de.ibikus.appa.hephaestus.servicecommunication.ordering.domain;

import java.util.regex.Pattern;

/** One item of an order. */
public record OrderLine(String sku, int quantity) {

    public static final String SKU_PATTERN = "[A-Z0-9-]{1,40}";
    public static final int MAX_QUANTITY = 1000;
    private static final Pattern SKU = Pattern.compile(SKU_PATTERN);

    public OrderLine {
        if (sku == null || !SKU.matcher(sku).matches()) {
            throw new InvalidValueException("SKU must match " + SKU_PATTERN + ": " + sku);
        }
        if (quantity < 1 || quantity > MAX_QUANTITY) {
            throw new InvalidValueException("Quantity must be 1.." + MAX_QUANTITY + ": " + quantity);
        }
    }
}
