package de.ibikus.appa.hephaestus.security.inventory.domain;

/** Units of one SKU in the warehouse: on hand, of which some are reserved for orders. */
public record StockItem(String sku, String name, int onHand, int reserved) {

    public StockItem {
        Skus.require(sku);
        if (onHand < 0 || reserved < 0 || reserved > onHand) {
            throw new InvalidValueException("Inconsistent stock for " + sku + ": " + onHand + "/" + reserved);
        }
    }

    public int available() {
        return onHand - reserved;
    }

    /** Returns the item with {@code quantity} more units reserved; never reserves more than is available. */
    public StockItem reserve(int quantity) {
        if (quantity <= 0) {
            throw new InvalidValueException("Quantity must be positive: " + quantity);
        }
        if (quantity > available()) {
            throw new InsufficientStockException(sku, available(), quantity);
        }
        return new StockItem(sku, name, onHand, reserved + quantity);
    }
}
