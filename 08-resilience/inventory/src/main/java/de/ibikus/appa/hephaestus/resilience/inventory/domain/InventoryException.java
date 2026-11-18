package de.ibikus.appa.hephaestus.resilience.inventory.domain;

/** Base of all business errors of the Inventory context. */
public abstract class InventoryException extends RuntimeException {

    protected InventoryException(String message) {
        super(message);
    }

    protected InventoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
