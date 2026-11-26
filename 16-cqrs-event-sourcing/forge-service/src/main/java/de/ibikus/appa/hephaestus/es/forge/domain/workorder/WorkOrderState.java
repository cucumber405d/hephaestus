package de.ibikus.appa.hephaestus.es.forge.domain.workorder;

import java.util.UUID;

/** A work order as its events say: rebuilt by folding the stream, never stored. */
public record WorkOrderState(
        String id, UUID orderId, String material, int quantity, Status status, String smith, String failureReason) {

    public enum Status {
        NEW,
        WAITING_FOR_MATERIAL,
        ALLOCATED,
        IN_PROGRESS,
        DONE,
        FAILED
    }

    public static final WorkOrderState INITIAL = new WorkOrderState(null, null, null, 0, Status.NEW, null, null);

    WorkOrderState with(Status next) {
        return new WorkOrderState(id, orderId, material, quantity, next, smith, failureReason);
    }
}
