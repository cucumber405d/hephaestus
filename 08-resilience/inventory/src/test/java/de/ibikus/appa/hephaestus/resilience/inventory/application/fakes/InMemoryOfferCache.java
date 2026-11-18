package de.ibikus.appa.hephaestus.resilience.inventory.application.fakes;

import de.ibikus.appa.hephaestus.resilience.inventory.application.port.out.OfferCache;
import de.ibikus.appa.hephaestus.resilience.inventory.domain.SupplierOffer;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** The cache in a map; can be switched to "broken" (every call throws), like a Redis that is down. */
public class InMemoryOfferCache implements OfferCache {

    private final Map<String, CachedOffer> entries = new ConcurrentHashMap<>();
    private volatile boolean broken;

    public void breakDown() {
        broken = true;
    }

    public int size() {
        return entries.size();
    }

    @Override
    public void put(SupplierOffer offer, Instant fetchedAt) {
        failIfBroken();
        entries.put(offer.sku(), new CachedOffer(offer, fetchedAt));
    }

    @Override
    public Optional<CachedOffer> get(String sku) {
        failIfBroken();
        return Optional.ofNullable(entries.get(sku));
    }

    private void failIfBroken() {
        if (broken) {
            throw new IllegalStateException("cache is down");
        }
    }
}
