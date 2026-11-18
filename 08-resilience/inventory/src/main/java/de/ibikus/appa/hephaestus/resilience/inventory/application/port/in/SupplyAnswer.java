package de.ibikus.appa.hephaestus.resilience.inventory.application.port.in;

import de.ibikus.appa.hephaestus.resilience.inventory.domain.SupplierOffer;
import java.time.Instant;

/**
 * An offer and where it came from: {@code live} from the supplier just now, or {@code cache} (the last live answer,
 * fetched at {@code fetchedAt}) because the supplier could not answer.
 */
public record SupplyAnswer(SupplierOffer offer, Source source, Instant fetchedAt, Mechanism mechanism) {

    public enum Source {
        LIVE,
        CACHE
    }
}
