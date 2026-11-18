package de.ibikus.appa.hephaestus.resilience.inventory.adapter.out.supplier;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.ibikus.appa.hephaestus.resilience.inventory.domain.SupplierOffer;
import java.math.BigDecimal;

/** The supplier's wire format; fields it adds later are ignored (tolerant reader). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OfferResponse(String sku, BigDecimal price, String currency, int leadTimeDays, int maxQuantity) {

    public SupplierOffer toDomain() {
        return new SupplierOffer(sku, price, currency, leadTimeDays, maxQuantity);
    }
}
