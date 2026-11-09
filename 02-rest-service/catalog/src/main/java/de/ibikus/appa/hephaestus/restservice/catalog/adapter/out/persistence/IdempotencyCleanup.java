package de.ibikus.appa.hephaestus.restservice.catalog.adapter.out.persistence;

import de.ibikus.appa.hephaestus.restservice.catalog.application.port.out.IdempotencyStore;
import java.time.Clock;
import java.time.Duration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Hourly removal of Idempotency-Key records older than 24 hours. */
@Component
public class IdempotencyCleanup {

    static final Duration RETENTION = Duration.ofHours(24);

    private final IdempotencyStore store;
    private final Clock clock;

    IdempotencyCleanup(IdempotencyStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    /** Returns how many keys were removed; with several instances each one may run it, which is harmless. */
    @Scheduled(cron = "0 0 * * * *")
    public int run() {
        return store.deleteOlderThan(clock.instant().minus(RETENTION));
    }
}
