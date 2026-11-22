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

    /** Work orders not finished yet (PLANNED, IN_PROGRESS): what the Strangler Fig waits for before decommission. */
    List<WorkOrder> open();

    boolean decommissioned();

    /**
     * What still keeps the local forge busy: open work orders plus requests published to it but not yet turned into
     * a work order (their listener has not run, failed, or waits for a republish). What drain waits for.
     */
    int busy();

    /**
     * Switches the local forge off for good. Refused while anything keeps it busy ({@link #busy()}).
     *
     * @throws ForgeStillBusy with the number of open work orders
     */
    void decommission();
}
