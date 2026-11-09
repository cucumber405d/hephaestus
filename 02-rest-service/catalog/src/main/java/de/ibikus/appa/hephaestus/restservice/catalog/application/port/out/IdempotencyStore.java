package de.ibikus.appa.hephaestus.restservice.catalog.application.port.out;

import java.time.Instant;
import java.util.Optional;

/** Driven port: Idempotency-Key bookkeeping. Every method runs in its own short transaction. */
public interface IdempotencyStore {

    /** Atomically claims the key; false if it already exists (completed or still in progress). */
    boolean reserve(String key, String requestHash, Instant now);

    /** Stores the final response of a reserved key. */
    void complete(String key, StoredResponse response);

    /** Releases a reservation whose request did not succeed, so the client may retry with the same key. */
    void release(String key);

    Optional<StoredResponse> find(String key);

    /** Deletes keys created before {@code cutoff}; returns how many were deleted. */
    int deleteOlderThan(Instant cutoff);
}
