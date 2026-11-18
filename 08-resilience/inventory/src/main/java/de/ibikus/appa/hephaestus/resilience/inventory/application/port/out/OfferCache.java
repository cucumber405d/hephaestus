package de.ibikus.appa.hephaestus.resilience.inventory.application.port.out;

import de.ibikus.appa.hephaestus.resilience.inventory.domain.SupplierOffer;
import java.time.Instant;
import java.util.Optional;

/** The last live offer per SKU, used only when the supplier cannot answer. Failures behave like a miss. */
public interface OfferCache {

    void put(SupplierOffer offer, Instant fetchedAt);

    Optional<CachedOffer> get(String sku);

    record CachedOffer(SupplierOffer offer, Instant fetchedAt) {}
}
