package de.ibikus.appa.hephaestus.servicecommunication.inventory.domain;

/** Fewer units available than requested (maps to 409). */
public class InsufficientStockException extends InventoryException {

    private final String sku;
    private final int available;

    public InsufficientStockException(String sku, int available, int requested) {
        super("Only " + available + " of " + sku + " available, " + requested + " requested");
        this.sku = sku;
        this.available = available;
    }

    public String sku() {
        return sku;
    }

    public int available() {
        return available;
    }
}
