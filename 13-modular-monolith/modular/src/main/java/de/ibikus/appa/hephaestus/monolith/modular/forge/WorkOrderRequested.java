package de.ibikus.appa.hephaestus.monolith.modular.forge;

import java.util.UUID;

/**
 * "Forge, please make this." Part of forge's API although ordering publishes it: the event types of both directions
 * live in forge, so the dependency stays ordering -> forge (types in both modules would be a cycle again).
 */
public record WorkOrderRequested(UUID orderId, String material, int quantity) {}
