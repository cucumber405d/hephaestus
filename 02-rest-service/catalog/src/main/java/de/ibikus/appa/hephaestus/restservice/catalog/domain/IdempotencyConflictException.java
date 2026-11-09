package de.ibikus.appa.hephaestus.restservice.catalog.domain;

/** An Idempotency-Key was reused with a different request, or its original request is unusable (maps to 409). */
public class IdempotencyConflictException extends CatalogException {

    public IdempotencyConflictException(String message) {
        super(message);
    }
}
