package de.ibikus.appa.hephaestus.eventskafka.ordering.domain;

/** Inventory cannot reserve the requested quantity (maps to 409). */
public class InsufficientStockException extends OrderingException {

    private final String sku;

    public InsufficientStockException(String sku) {
        super("Not enough stock for " + sku);
        this.sku = sku;
    }

    public String sku() {
        return sku;
    }
}
