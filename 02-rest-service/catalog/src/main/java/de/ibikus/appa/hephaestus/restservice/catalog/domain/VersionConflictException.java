package de.ibikus.appa.hephaestus.restservice.catalog.domain;

/** The expected version (If-Match) does not match the current one (maps to 412). */
public class VersionConflictException extends CatalogException {

    public VersionConflictException(String message) {
        super(message);
    }
}
