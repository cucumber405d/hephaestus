package de.ibikus.appa.hephaestus.monolith.forge.application;

import de.ibikus.appa.hephaestus.monolith.forge.domain.WorkOrder;

/** Port: tells the rest of the world (the monolith's ordering) how a work order ended. */
public interface Outcomes {

    void completed(WorkOrder workOrder);

    void failed(WorkOrder workOrder, String reason);
}
