package de.ibikus.appa.hephaestus.resilience.inventory.application.port.out;

import de.ibikus.appa.hephaestus.resilience.inventory.application.port.in.Mechanism;
import de.ibikus.appa.hephaestus.resilience.inventory.domain.SupplierOffer;

/** One protected way of asking the supplier; there is one implementation per {@link Mechanism}. */
public interface SupplierOffers {

    Mechanism mechanism();

    /**
     * @throws de.ibikus.appa.hephaestus.resilience.inventory.domain.UnknownOfferException the supplier answered 404
     * @throws de.ibikus.appa.hephaestus.resilience.inventory.domain.SupplierUnavailableException any failure or refusal of the call
     */
    SupplierOffer fetch(String sku);
}
