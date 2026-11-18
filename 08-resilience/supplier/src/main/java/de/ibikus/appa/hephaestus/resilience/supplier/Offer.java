package de.ibikus.appa.hephaestus.resilience.supplier;

import java.math.BigDecimal;

/** What the supplier can deliver for one SKU. */
public record Offer(String sku, BigDecimal price, String currency, int leadTimeDays, int maxQuantity) {}
