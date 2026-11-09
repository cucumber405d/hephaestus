package de.ibikus.appa.hephaestus.restservice.catalog.domain;

/** A category with the same name, ignoring case, already exists (maps to 409). */
public class DuplicateCategoryNameException extends CatalogException {

    public DuplicateCategoryNameException(String message) {
        super(message);
    }
}
