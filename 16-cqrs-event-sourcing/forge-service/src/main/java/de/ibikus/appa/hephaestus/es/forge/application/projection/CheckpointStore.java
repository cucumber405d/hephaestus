package de.ibikus.appa.hephaestus.es.forge.application.projection;

import de.ibikus.appa.hephaestus.es.forge.application.eventstore.Checkpoint;

/** Where each reader of the journal stopped (read.projection_checkpoint). */
public interface CheckpointStore {

    /** The checkpoint, locked for the current transaction: one writer per projection. */
    Checkpoint lock(String name);

    /** The checkpoint without a lock (status). */
    Checkpoint get(String name);

    void save(String name, Checkpoint checkpoint);

    /** Journal events after the checkpoint (not locked). */
    long lag(String name);
}
