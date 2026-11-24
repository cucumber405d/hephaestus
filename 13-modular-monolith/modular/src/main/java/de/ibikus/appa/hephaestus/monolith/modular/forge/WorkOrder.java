package de.ibikus.appa.hephaestus.monolith.modular.forge;

import java.util.UUID;

/** The smith's view of a work order; the same JSON as the legacy's and the service's ({@code origin} tells apart). */
public record WorkOrder(String id, UUID orderId, String material, int quantity, String status, String origin) {}
