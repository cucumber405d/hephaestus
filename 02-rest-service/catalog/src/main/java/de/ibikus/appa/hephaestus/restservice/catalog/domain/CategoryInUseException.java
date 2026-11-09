package de.ibikus.appa.hephaestus.restservice.catalog.domain;

/** A category that still has items cannot be deleted (maps to 409). */
public class CategoryInUseException extends CatalogException {

    public CategoryInUseException(String message) {
        super(message);
    }
}
