package de.ibikus.appa.hephaestus.monolith.forge.domain;

import java.util.UUID;

/** A work order of the service: id {@code FW-<uuid>}, one per customer order. */
public record WorkOrder(String id, UUID orderId, String material, int quantity, Status status, boolean materialReady) {

    public enum Status {
        PLANNED,
        IN_PROGRESS,
        DONE,
        FAILED
    }

    public static WorkOrder requested(UUID orderId, String material, int quantity) {
        return new WorkOrder("FW-" + UUID.randomUUID(), orderId, material, quantity, Status.PLANNED, false);
    }
}
