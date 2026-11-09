package de.ibikus.appa.hephaestus.restservice.catalog.domain;

/** No category with the given id (maps to 404). */
public class CategoryNotFoundException extends CatalogException {

    public CategoryNotFoundException(String message) {
        super(message);
    }
}
