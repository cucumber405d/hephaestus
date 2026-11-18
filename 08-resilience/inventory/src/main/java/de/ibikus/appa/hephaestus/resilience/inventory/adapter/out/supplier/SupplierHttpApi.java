package de.ibikus.appa.hephaestus.resilience.inventory.adapter.out.supplier;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;

/** The supplier's offer API as a Spring Framework 7 HTTP interface; unprotected, one plain call. */
public interface SupplierHttpApi {

    @GetExchange("/api/offers/{sku}")
    OfferResponse offer(@PathVariable String sku);
}
