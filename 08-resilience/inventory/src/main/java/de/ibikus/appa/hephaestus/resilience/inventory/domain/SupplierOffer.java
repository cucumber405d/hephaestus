package de.ibikus.appa.hephaestus.resilience.inventory.domain;

import java.math.BigDecimal;
import java.util.Objects;

/** What the external supplier offers for one SKU: price, lead time and the most it can deliver. */
public record SupplierOffer(String sku, BigDecimal price, String currency, int leadTimeDays, int maxQuantity) {

    public SupplierOffer {
        Objects.requireNonNull(sku, "sku");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(currency, "currency");
    }
}
