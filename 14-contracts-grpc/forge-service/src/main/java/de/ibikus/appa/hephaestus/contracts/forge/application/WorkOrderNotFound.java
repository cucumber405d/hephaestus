package de.ibikus.appa.hephaestus.contracts.forge.application;

public class WorkOrderNotFound extends RuntimeException {

    public WorkOrderNotFound(String id) {
        super("work order " + id);
    }
}
