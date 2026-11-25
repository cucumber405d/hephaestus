package de.ibikus.appa.hephaestus.cdc.ordering.ordering;

import java.util.UUID;

/** {@code priceCents} and {@code leadTimeDays}: Forge's quote, for custom orders it accepted. */
public record CustomerOrder(UUID id, String status, String reason, Long priceCents, Integer leadTimeDays) {}
