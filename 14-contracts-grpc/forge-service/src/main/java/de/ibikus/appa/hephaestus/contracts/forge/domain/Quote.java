package de.ibikus.appa.hephaestus.contracts.forge.domain;

import java.time.Instant;

/** A binding quote: Forge builds {@code quantity} of {@code material} for this price until {@code expiresAt}. */
public record Quote(
        String id, String material, int quantity, long priceCents, int leadTimeDays, Instant expiresAt) {}
