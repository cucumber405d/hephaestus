package de.ibikus.appa.hephaestus.es.forge.application.eventstore;

/**
 * Where a reader of the whole journal stopped: the last event's transaction and position. Both are needed — one
 * transaction may hold positions on both sides of another transaction's events (ADR-0018).
 */
public record Checkpoint(String txId, long position) {

    public static final Checkpoint START = new Checkpoint("0", 0);

    public static Checkpoint of(RecordedEvent event) {
        return new Checkpoint(event.txId(), event.position());
    }
}
