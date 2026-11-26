package de.ibikus.appa.hephaestus.es.forge.application.projection;

import de.ibikus.appa.hephaestus.es.forge.application.eventstore.RecordedEvent;

/**
 * A read model fed from the journal. {@code apply} sees every event (decoded) in journal order and ignores the ones it
 * does not need; it runs in the batch's transaction together with the checkpoint, so it must be idempotent only
 * towards a {@link #reset()}, never towards a partial batch.
 */
public interface Projection {

    String name();

    void apply(Object event, RecordedEvent meta);

    /** Empties the read model for a rebuild from the journal's start. */
    void reset();
}
