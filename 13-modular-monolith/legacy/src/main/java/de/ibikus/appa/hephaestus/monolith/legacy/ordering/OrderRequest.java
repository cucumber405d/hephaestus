package de.ibikus.appa.hephaestus.monolith.legacy.ordering;

/**
 * Either a stock item ({@code sku}) or a custom piece ({@code custom}, {@code material}, {@code quantity}).
 * {@code custom} is a wrapper: Jackson 3 refuses a missing value for a primitive (FAIL_ON_NULL_FOR_PRIMITIVES).
 */
public record OrderRequest(String customerId, String sku, Boolean custom, String material, Integer quantity) {

    public boolean isCustom() {
        return Boolean.TRUE.equals(custom);
    }

    public int quantityOrOne() {
        return quantity == null ? 1 : quantity;
    }
}
