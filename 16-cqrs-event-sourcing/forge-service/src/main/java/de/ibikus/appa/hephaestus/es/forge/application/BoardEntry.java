package de.ibikus.appa.hephaestus.es.forge.application;

import java.util.UUID;

/** A line of the workshop board (read model): the smith's list. */
public record BoardEntry(
        String id, UUID orderId, String material, int quantity, String status, String smith, String failureReason) {}
