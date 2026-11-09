package de.ibikus.appa.hephaestus.restservice.catalog.adapter.in.web;

import de.ibikus.appa.hephaestus.restservice.catalog.application.fakes.InMemoryIdempotencyStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** Beans every @WebMvcTest needs because the slice also loads servlet filters (IdempotencyFilter). */
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
