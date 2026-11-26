package de.ibikus.appa.hephaestus.es.forge.application;

public class WorkOrderNotFound extends RuntimeException {

    public WorkOrderNotFound(String id) {
        super("work order " + id);
    }
}
