package de.ibikus.appa.hephaestus.restservice.catalog.domain;

/** Base class of all catalog domain exceptions; adapters translate them into HTTP problems. */
public abstract class CatalogException extends RuntimeException {

    protected CatalogException(String message) {
        super(message);
    }
}
