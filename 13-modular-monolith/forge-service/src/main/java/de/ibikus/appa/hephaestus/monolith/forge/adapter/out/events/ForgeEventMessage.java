package de.ibikus.appa.hephaestus.monolith.forge.adapter.out.events;

import java.util.UUID;

/**
 * The outcome as the monolith reads it from Kafka: {@code type} is {@code WorkOrderCompleted} or
 * {@code WorkOrderFailed}; {@code eventId} lets the monolith drop a redelivery.
 */
public record ForgeEventMessage(UUID eventId, String type, UUID orderId, String workOrderId, String reason) {}
