package de.ibikus.appa.hephaestus.restservice.catalog.domain;

/** An item refers to a category that does not exist (maps to 422). */
public class UnknownCategoryException extends CatalogException {

    public UnknownCategoryException(String message) {
        super(message);
    }
}
