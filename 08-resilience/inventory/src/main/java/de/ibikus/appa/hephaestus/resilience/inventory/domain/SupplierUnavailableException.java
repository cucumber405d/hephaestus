package de.ibikus.appa.hephaestus.resilience.inventory.domain;

/**
 * The supplier gave no usable answer: timeout, connection refused, 5xx, or a resilience mechanism refused the call
 * (circuit open, bulkhead full, rate limit). The caller may fall back to cached data.
 */
public class SupplierUnavailableException extends InventoryException {

    public SupplierUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
