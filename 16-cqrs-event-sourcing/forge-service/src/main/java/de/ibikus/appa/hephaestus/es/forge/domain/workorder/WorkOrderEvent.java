package de.ibikus.appa.hephaestus.es.forge.domain.workorder;

import java.util.UUID;

/** What happened to a work order — the journal's events of stream type {@code WorkOrder}. */
public sealed interface WorkOrderEvent {

    record WorkOrderRequested(String workOrderId, UUID orderId, String material, int quantity)
            implements WorkOrderEvent {}

    record WorkOrderFailed(String reason) implements WorkOrderEvent {}

    record MaterialAllocated(int quantity) implements WorkOrderEvent {}

    /** Schema version 2: version 1 had no smith (upcasted to "unknown"). */
    record WorkOrderStarted(String smith) implements WorkOrderEvent {}

    record WorkOrderCompleted() implements WorkOrderEvent {}
}
