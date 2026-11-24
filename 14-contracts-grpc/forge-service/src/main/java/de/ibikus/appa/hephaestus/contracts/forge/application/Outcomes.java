package de.ibikus.appa.hephaestus.contracts.forge.application;

import de.ibikus.appa.hephaestus.contracts.forge.domain.WorkOrder;

/** Port: tells the rest of the world (Ordering) how a work order ended. */
public interface Outcomes {

    void completed(WorkOrder workOrder);

    void failed(WorkOrder workOrder, String reason);
}
