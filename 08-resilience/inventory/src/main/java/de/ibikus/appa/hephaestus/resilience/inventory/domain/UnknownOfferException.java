package de.ibikus.appa.hephaestus.resilience.inventory.domain;

/** The supplier has no offer for the SKU (its 404): a valid answer, not a failure of the supplier. */
public class UnknownOfferException extends InventoryException {

    private final String sku;

    public UnknownOfferException(String sku) {
        super("The supplier has no offer for " + sku);
        this.sku = sku;
    }

    public String sku() {
        return sku;
    }
}
