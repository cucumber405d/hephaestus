package de.ibikus.appa.hephaestus.restservice.catalog.application.port.in;

import de.ibikus.appa.hephaestus.restservice.catalog.domain.Item;

/** The created item, or the item an earlier request with the same Idempotency-Key created ({@code replayed}). */
public record CreationResult(Item item, boolean replayed) {}
