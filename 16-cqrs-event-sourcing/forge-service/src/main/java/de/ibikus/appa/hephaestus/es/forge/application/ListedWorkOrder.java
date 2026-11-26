package de.ibikus.appa.hephaestus.es.forge.application;

import java.util.UUID;

/** A line of the smith's list; {@code origin} is kept from 13 (always SERVICE here). */
public record ListedWorkOrder(String id, UUID orderId, String material, int quantity, String status, String origin) {}
