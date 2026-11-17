package de.ibikus.appa.hephaestus.security.ordering.application.port.in;

public interface AvailabilityQuery {

    /** Units of this SKU that can be ordered right now (asked from Inventory, not cached). */
    int available(String sku);
}
