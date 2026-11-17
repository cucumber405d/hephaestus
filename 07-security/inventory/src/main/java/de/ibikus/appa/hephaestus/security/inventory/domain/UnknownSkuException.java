package de.ibikus.appa.hephaestus.security.inventory.domain;

/** A reservation names a SKU that is not stocked (maps to 422). */
public class UnknownSkuException extends InventoryException {

    private final String sku;

    public UnknownSkuException(String sku) {
        super("Unknown SKU " + sku);
        this.sku = sku;
    }

    public String sku() {
        return sku;
    }
}
