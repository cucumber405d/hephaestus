package de.ibikus.appa.hephaestus.restservice.catalog.domain;

/** No item with the given id (maps to 404). */
public class ItemNotFoundException extends CatalogException {

    public ItemNotFoundException(String message) {
        super(message);
    }
}
