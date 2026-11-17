package de.ibikus.appa.hephaestus.security.inventory.domain;

import java.util.regex.Pattern;

/** SKU format shared by the domain types. */
public final class Skus {

    public static final String PATTERN = "[A-Z0-9-]{1,40}";
    private static final Pattern COMPILED = Pattern.compile(PATTERN);

    private Skus() {}

    public static String require(String sku) {
        if (sku == null || !COMPILED.matcher(sku).matches()) {
            throw new InvalidValueException("SKU must match " + PATTERN + ": " + sku);
        }
        return sku;
    }
}
