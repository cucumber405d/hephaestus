package de.ibikus.appa.hephaestus.security.inventory.domain;

/** One SKU and how many units of it a reservation holds. */
public record ReservationLine(String sku, int quantity) {

    public static final int MAX_QUANTITY = 1000;

    public ReservationLine {
        Skus.require(sku);
        if (quantity < 1 || quantity > MAX_QUANTITY) {
            throw new InvalidValueException("Quantity must be 1.." + MAX_QUANTITY + ": " + quantity);
        }
    }
}
