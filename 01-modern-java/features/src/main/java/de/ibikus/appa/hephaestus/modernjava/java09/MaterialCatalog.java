package de.ibikus.appa.hephaestus.modernjava.java09;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Java 9 collection factories: {@code Map.of} returns an immutable map that rejects null keys/values and
 * has an unspecified iteration order.
 */
public final class MaterialCatalog {

    private static final Map<String, Material> STANDARD = Map.of(
            "BRZ", new Material("BRZ", "Bronze", new BigDecimal("12.50")),
            "IRN", new Material("IRN", "Iron", new BigDecimal("3.20")),
            "STL", new Material("STL", "Steel", new BigDecimal("4.80")));

    private MaterialCatalog() {}

    public static Map<String, Material> standard() {
        return STANDARD;
    }
}
