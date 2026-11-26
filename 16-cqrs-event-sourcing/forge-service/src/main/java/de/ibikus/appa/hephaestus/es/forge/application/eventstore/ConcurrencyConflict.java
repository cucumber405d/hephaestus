package de.ibikus.appa.hephaestus.es.forge.application.eventstore;

/** Another writer appended to the stream first: the command was decided on a stale state. */
public class ConcurrencyConflict extends RuntimeException {

    public ConcurrencyConflict(String streamId, int expectedVersion) {
        super(streamId + ": expected version " + expectedVersion + " is no longer current");
    }
}
