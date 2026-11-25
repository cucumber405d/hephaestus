package de.ibikus.appa.hephaestus.cdc.forge.application;

import de.ibikus.appa.hephaestus.cdc.forge.domain.WorkOrder;
import de.ibikus.appa.hephaestus.cdc.forge.domain.WorkOrder.Status;
import java.util.List;
import java.util.Optional;

/** Port: the work orders in the service's database. */
public interface WorkOrderStore {

    /** False when a work order for the same customer order exists already. */
    boolean createIfAbsent(WorkOrder workOrder);

    Optional<WorkOrder> find(String id);

    /** Locks the row; empty unless it is PLANNED and still waiting for material. */
    Optional<WorkOrder> lockWaitingForMaterial(String id);

    List<WorkOrder> list(Status status);

    List<String> waitingForMaterial();

    /** True when the row was in {@code from} (and, if asked, had its material) and is now {@code to}. */
    boolean transition(String id, Status from, Status to, boolean requireMaterial);

    void materialReady(String id);

    void failed(String id, String reason);
}
