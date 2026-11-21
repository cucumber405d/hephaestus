package de.ibikus.appa.hephaestus.monolith.legacy.ordering;

/** Either a stock item ({@code sku}) or a custom piece ({@code custom}, {@code material}, {@code quantity}). */
public record OrderRequest(String customerId, String sku, boolean custom, String material, Integer quantity) {}
