package de.ibikus.appa.hephaestus.cdc.forge.application;

import de.ibikus.appa.hephaestus.cdc.forge.domain.WorkOrder;

/** Port: tells the rest of the world (Ordering) how a work order ended. */
public interface Outcomes {

    void completed(WorkOrder workOrder);

    void failed(WorkOrder workOrder, String reason);
}
