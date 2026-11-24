package de.ibikus.appa.hephaestus.contracts.forge.application;

/** A transition from the wrong state: done already, or the material is not there yet. */
public class WorkOrderConflict extends RuntimeException {

    public WorkOrderConflict(String message) {
        super(message);
    }
}
