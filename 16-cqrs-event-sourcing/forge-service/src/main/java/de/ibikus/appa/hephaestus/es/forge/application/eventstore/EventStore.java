package de.ibikus.appa.hephaestus.es.forge.application.eventstore;

import java.util.List;

/** The journal. All operations join the caller's transaction. */
public interface EventStore {

    /**
     * Appends {@code events} as versions {@code expectedVersion + 1 ...} of the stream; {@code expectedVersion} 0 means
     * a new stream.
     *
     * @throws ConcurrencyConflict when the stream's current version is not {@code expectedVersion}
     */
    void append(String streamId, String streamType, int expectedVersion, List<NewEvent> events);

    /** The stream's events in version order; empty for an unknown stream. */
    List<RecordedEvent> readStream(String streamId);

    /**
     * Events after {@code after} in (transaction, position) order, only from transactions older than every
     * transaction still open — so an event committed later can never land behind a reader's checkpoint.
     */
    List<RecordedEvent> readAll(Checkpoint after, int limit);
}
