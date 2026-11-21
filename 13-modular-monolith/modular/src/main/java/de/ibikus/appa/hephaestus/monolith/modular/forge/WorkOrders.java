package de.ibikus.appa.hephaestus.monolith.modular.forge;

import java.util.List;
import java.util.UUID;

/** Forge's API: work orders for the smith. Creation happens through {@link WorkOrderRequested}. */
public interface WorkOrders {

    List<WorkOrder> list(String status);

    void start(String id);

    void complete(String id);

    /** Creates the work order for {@code orderId} once; a repeated request for the same order changes nothing. */
    void request(UUID orderId, String material, int quantity);
}
