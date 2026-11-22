package de.ibikus.appa.hephaestus.monolith.forge.adapter.out.events;

import java.util.UUID;
import org.springframework.modulith.events.Externalized;

/**
 * The outcome as the monolith reads it from Kafka: {@code type} is {@code WorkOrderCompleted} or
 * {@code WorkOrderFailed}; {@code eventId} lets the monolith drop a redelivery. Externalized by Spring Modulith after
 * the work order's transaction commits, tracked in the publication registry (the outbox), key = the customer order.
 */
@Externalized("forge.events::#{#this.orderId()}")
public record ForgeEventMessage(UUID eventId, String type, UUID orderId, String workOrderId, String reason) {}
