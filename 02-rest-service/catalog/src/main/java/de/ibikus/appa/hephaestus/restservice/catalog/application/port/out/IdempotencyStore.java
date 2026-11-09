package de.ibikus.appa.hephaestus.restservice.catalog.application.port.out;

import java.time.Instant;
import java.util.Optional;

/** Driven port: Idempotency-Key bookkeeping, used inside the transaction that creates the item. */
public interface IdempotencyStore {

    Optional<IdempotencyRecord> find(String key);

    /** Inserts the key; if a concurrent transaction inserted it first, raises IdempotencyConflictException. */
    void insert(IdempotencyRecord record);

    /** Deletes keys created before {@code cutoff}; returns how many were deleted. */
    int deleteOlderThan(Instant cutoff);
}
