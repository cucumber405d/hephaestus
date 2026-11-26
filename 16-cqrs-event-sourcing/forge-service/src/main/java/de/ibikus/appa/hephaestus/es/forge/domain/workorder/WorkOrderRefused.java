package de.ibikus.appa.hephaestus.es.forge.domain.workorder;

/** The command does not fit the work order's state (409 at the edge). */
public class WorkOrderRefused extends RuntimeException {

    public WorkOrderRefused(String message) {
        super(message);
    }
}
