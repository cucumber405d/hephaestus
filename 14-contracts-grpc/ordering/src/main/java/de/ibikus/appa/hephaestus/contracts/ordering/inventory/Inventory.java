package de.ibikus.appa.hephaestus.contracts.ordering.inventory;

/** Finished goods on the shelf; the only way into inventory's schema. */
public interface Inventory {

    boolean reserve(String sku, int quantity);
}
