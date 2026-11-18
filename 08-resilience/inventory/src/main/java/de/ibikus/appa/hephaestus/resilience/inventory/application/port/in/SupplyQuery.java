package de.ibikus.appa.hephaestus.resilience.inventory.application.port.in;

public interface SupplyQuery {

    /**
     * The supplier's offer for the SKU, fetched through the given mechanism; the cached one if the supplier is
     * unavailable.
     *
     * @throws de.ibikus.appa.hephaestus.resilience.inventory.domain.UnknownOfferException the supplier has no offer for the SKU
     * @throws de.ibikus.appa.hephaestus.resilience.inventory.domain.SupplierUnavailableException no live answer and nothing cached
     */
    SupplyAnswer offer(Mechanism mechanism, String sku);
}
