package de.ibikus.appa.hephaestus.containersci.catalog.domain;

/** A value violates a domain invariant (maps to 400). */
public class InvalidValueException extends CatalogException {

    public InvalidValueException(String message) {
        super(message);
    }
}
