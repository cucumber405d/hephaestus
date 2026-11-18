package de.ibikus.appa.hephaestus.security.inventory.domain;

/** Base of all business errors of the Inventory context. */
public abstract class InventoryException extends RuntimeException {

    protected InventoryException(String message) {
        super(message);
    }
}
