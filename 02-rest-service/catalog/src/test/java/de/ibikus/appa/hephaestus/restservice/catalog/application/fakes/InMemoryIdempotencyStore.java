package de.ibikus.appa.hephaestus.restservice.catalog.application.fakes;

import de.ibikus.appa.hephaestus.restservice.catalog.application.port.out.IdempotencyRecord;
import de.ibikus.appa.hephaestus.restservice.catalog.application.port.out.IdempotencyStore;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.IdempotencyConflictException;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory Idempotency-Key store with the same duplicate-key behaviour as the database. */
public class InMemoryIdempotencyStore implements IdempotencyStore {

    private final ConcurrentHashMap<String, IdempotencyRecord> store = new ConcurrentHashMap<>();

    public void clear() {
        store.clear();
    }

    @Override
    public Optional<IdempotencyRecord> find(String key) {
        return Optional.ofNullable(store.get(key));
    }

    @Override
    public void insert(IdempotencyRecord record) {
        if (store.putIfAbsent(record.key(), record) != null) {
            throw new IdempotencyConflictException("Idempotency-Key inserted concurrently");
        }
    }

    @Override
    public int deleteOlderThan(Instant cutoff) {
        int before = store.size();
        store.values().removeIf(record -> record.createdAt().isBefore(cutoff));
        return before - store.size();
    }
}
