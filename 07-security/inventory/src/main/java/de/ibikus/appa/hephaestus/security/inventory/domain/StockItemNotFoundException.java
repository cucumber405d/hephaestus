package de.ibikus.appa.hephaestus.security.inventory.domain;

/** No stock item with the requested SKU (maps to 404). */
public class StockItemNotFoundException extends InventoryException {

    public StockItemNotFoundException(String sku) {
        super("Stock item " + sku + " not found");
    }
}
