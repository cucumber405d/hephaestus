package de.ibikus.appa.hephaestus.restservice.catalog.application.fakes;

import de.ibikus.appa.hephaestus.restservice.catalog.application.port.out.IdempotencyStore;
import de.ibikus.appa.hephaestus.restservice.catalog.application.port.out.StoredResponse;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory Idempotency-Key store for web slice tests. */
public class InMemoryIdempotencyStore implements IdempotencyStore {

    private final ConcurrentHashMap<String, StoredResponse> store = new ConcurrentHashMap<>();

    public void clear() {
        store.clear();
    }

    @Override
    public boolean reserve(String key, String requestHash, Instant now) {
        return store.putIfAbsent(key, new StoredResponse(requestHash, 0, null, null, null)) == null;
    }

    @Override
    public void complete(String key, StoredResponse response) {
        store.put(key, response);
    }

    @Override
    public void release(String key) {
        store.remove(key);
    }

    @Override
    public Optional<StoredResponse> find(String key) {
        return Optional.ofNullable(store.get(key));
    }

    @Override
    public int deleteOlderThan(Instant cutoff) {
        return 0;
    }
}
