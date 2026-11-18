package de.ibikus.appa.hephaestus.security.ordering.domain;

/** Inventory did not answer in time, refused the connection or failed with 5xx (maps to 503). */
public class InventoryUnavailableException extends OrderingException {

    public InventoryUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
