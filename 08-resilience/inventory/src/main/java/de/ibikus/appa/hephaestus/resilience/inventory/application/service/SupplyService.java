package de.ibikus.appa.hephaestus.resilience.inventory.application.service;

import de.ibikus.appa.hephaestus.resilience.inventory.application.port.in.Mechanism;
import de.ibikus.appa.hephaestus.resilience.inventory.application.port.in.SupplyAnswer;
import de.ibikus.appa.hephaestus.resilience.inventory.application.port.in.SupplyAnswer.Source;
import de.ibikus.appa.hephaestus.resilience.inventory.application.port.in.SupplyQuery;
import de.ibikus.appa.hephaestus.resilience.inventory.application.port.out.OfferCache;
import de.ibikus.appa.hephaestus.resilience.inventory.application.port.out.SupplierOffers;
import de.ibikus.appa.hephaestus.resilience.inventory.domain.SupplierOffer;
import de.ibikus.appa.hephaestus.resilience.inventory.domain.SupplierUnavailableException;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Asks the supplier through the chosen mechanism and keeps the last live answer; when the supplier cannot answer,
 * returns that cached answer instead. The fallback lives here, once, so the three adapters differ only in how they
 * protect the call (the comparison stays fair).
 */
@Service
public class SupplyService implements SupplyQuery {

    private static final Logger LOG = LoggerFactory.getLogger(SupplyService.class);

    private final Map<Mechanism, SupplierOffers> mechanisms = new EnumMap<>(Mechanism.class);
    private final OfferCache cache;
    private final Clock clock;

    public SupplyService(List<SupplierOffers> adapters, OfferCache cache, Clock clock) {
        adapters.forEach(adapter -> mechanisms.put(adapter.mechanism(), adapter));
        this.cache = cache;
        this.clock = clock;
    }

    @Override
    public SupplyAnswer offer(Mechanism mechanism, String sku) {
        var adapter = mechanisms.get(mechanism);
        if (adapter == null) {
            throw new IllegalStateException("No adapter for " + mechanism);
        }
        try {
            var offer = adapter.fetch(sku);
            var now = clock.instant();
            remember(offer, now);
            return new SupplyAnswer(offer, Source.LIVE, now, mechanism);
        } catch (SupplierUnavailableException e) {
            LOG.warn("Supplier unavailable via {} for {}: {}", mechanism.id(), sku, e.getMessage());
            return cached(sku)
                    .map(cached -> new SupplyAnswer(cached.offer(), Source.CACHE, cached.fetchedAt(), mechanism))
                    .orElseThrow(() -> e);
        }
    }

    /** The cache is a convenience: a broken cache must never break a live answer. */
    private void remember(SupplierOffer offer, Instant fetchedAt) {
        try {
            cache.put(offer, fetchedAt);
        } catch (RuntimeException e) {
            LOG.warn("Could not cache the offer for {}: {}", offer.sku(), e.toString());
        }
    }

    private Optional<OfferCache.CachedOffer> cached(String sku) {
        try {
            return cache.get(sku);
        } catch (RuntimeException e) {
            LOG.warn("Could not read the cached offer for {}: {}", sku, e.toString());
            return Optional.empty();
        }
    }
}
