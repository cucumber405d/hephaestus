package de.ibikus.appa.hephaestus.cdc.forge.application;

public class WorkOrderNotFound extends RuntimeException {

    public WorkOrderNotFound(String id) {
        super("work order " + id);
    }
}
