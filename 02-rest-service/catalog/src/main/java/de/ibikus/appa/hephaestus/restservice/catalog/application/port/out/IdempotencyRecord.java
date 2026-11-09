package de.ibikus.appa.hephaestus.restservice.catalog.application.port.out;

import de.ibikus.appa.hephaestus.restservice.catalog.domain.ItemId;
import java.time.Instant;

/** An Idempotency-Key with the hash of the request it was first used for and the item that request created. */
public record IdempotencyRecord(String key, String requestHash, ItemId itemId, Instant createdAt) {}
