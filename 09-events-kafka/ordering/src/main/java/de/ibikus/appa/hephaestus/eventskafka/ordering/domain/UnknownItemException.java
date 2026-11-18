package de.ibikus.appa.hephaestus.eventskafka.ordering.domain;

/** The order names an item Inventory does not know (maps to 422). */
public class UnknownItemException extends OrderingException {

    private final String sku;

    public UnknownItemException(String sku) {
        super("Unknown item " + sku);
        this.sku = sku;
    }

    public String sku() {
        return sku;
    }
}
