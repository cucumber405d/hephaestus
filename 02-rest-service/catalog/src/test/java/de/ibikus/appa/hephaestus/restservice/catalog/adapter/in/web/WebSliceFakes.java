package de.ibikus.appa.hephaestus.restservice.catalog.adapter.in.web;

import de.ibikus.appa.hephaestus.restservice.catalog.application.fakes.InMemoryIdempotencyStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** Shared fakes for the @WebMvcTest slices: the Idempotency-Key store and a fixed clock. */
@TestConfiguration
public class WebSliceFakes {

    public static final Instant NOW = Instant.parse("2026-11-09T10:00:00.123456Z");

    @Bean
    InMemoryIdempotencyStore idempotencyStore() {
        return new InMemoryIdempotencyStore();
    }

    @Bean
    Clock clock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }
}
