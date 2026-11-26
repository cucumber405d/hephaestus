package de.ibikus.appa.hephaestus.es.forge.application.projection;

public class ProjectionNotFound extends RuntimeException {

    public ProjectionNotFound(String name) {
        super("no projection " + name);
    }
}
