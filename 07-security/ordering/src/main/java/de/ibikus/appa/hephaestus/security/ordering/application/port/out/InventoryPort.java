package de.ibikus.appa.hephaestus.security.ordering.application.port.out;

import de.ibikus.appa.hephaestus.security.ordering.domain.OrderLine;
import java.util.List;
import java.util.UUID;

/**
 * What Ordering needs from Inventory, in Ordering's own terms. Failures arrive as Ordering exceptions
 * (InsufficientStockException, UnknownItemException, InventoryUnavailableException), never as HTTP types.
 */
public interface InventoryPort {

    /** Reserves all lines for the order, idempotently per order id; returns the reservation id. */
    UUID reserve(UUID orderId, List<OrderLine> lines);

    int available(String sku);
}
