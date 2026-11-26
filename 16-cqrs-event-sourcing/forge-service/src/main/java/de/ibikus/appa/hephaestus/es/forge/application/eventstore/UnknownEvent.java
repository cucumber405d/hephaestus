package de.ibikus.appa.hephaestus.es.forge.application.eventstore;

/** A journal row this build cannot read: an unknown type, or a schema version newer than it knows. Never skipped. */
public class UnknownEvent extends RuntimeException {

    public UnknownEvent(String message) {
        super(message);
    }
}
