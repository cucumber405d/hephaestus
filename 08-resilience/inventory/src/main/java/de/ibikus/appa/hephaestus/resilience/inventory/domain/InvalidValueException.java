package de.ibikus.appa.hephaestus.resilience.inventory.domain;

/** A value violates a domain invariant (maps to 400). */
public class InvalidValueException extends InventoryException {

    public InvalidValueException(String message) {
        super(message);
    }
}
