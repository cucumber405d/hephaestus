package de.ibikus.appa.hephaestus.es.forge.domain.workorder;

import de.ibikus.appa.hephaestus.es.forge.domain.workorder.WorkOrderCommand.Allocate;
import de.ibikus.appa.hephaestus.es.forge.domain.workorder.WorkOrderCommand.Complete;
import de.ibikus.appa.hephaestus.es.forge.domain.workorder.WorkOrderCommand.Fail;
import de.ibikus.appa.hephaestus.es.forge.domain.workorder.WorkOrderCommand.Request;
import de.ibikus.appa.hephaestus.es.forge.domain.workorder.WorkOrderCommand.Start;
import de.ibikus.appa.hephaestus.es.forge.domain.workorder.WorkOrderEvent.MaterialAllocated;
import de.ibikus.appa.hephaestus.es.forge.domain.workorder.WorkOrderEvent.WorkOrderCompleted;
import de.ibikus.appa.hephaestus.es.forge.domain.workorder.WorkOrderEvent.WorkOrderFailed;
import de.ibikus.appa.hephaestus.es.forge.domain.workorder.WorkOrderEvent.WorkOrderRequested;
import de.ibikus.appa.hephaestus.es.forge.domain.workorder.WorkOrderEvent.WorkOrderStarted;
import de.ibikus.appa.hephaestus.es.forge.domain.workorder.WorkOrderState.Status;
import java.util.List;
import java.util.UUID;

/** The work order aggregate as two pure functions: decide (command, state → events) and evolve (state, event → state). */
public final class WorkOrderDecider {

    private WorkOrderDecider() {}

    /** One work order per customer order: the id — and the stream — follow from the order. */
    public static String idFor(UUID orderId) {
        return "FW-" + orderId;
    }

    public static List<WorkOrderEvent> decide(WorkOrderCommand command, WorkOrderState state) {
        return switch (command) {
            case Request r ->
                state.status() == Status.NEW
                        ? List.of(new WorkOrderRequested(idFor(r.orderId()), r.orderId(), r.material(), r.quantity()))
                        : List.of(); // a redelivered request: already known
            case Fail f -> {
                require(state, Status.WAITING_FOR_MATERIAL, "cannot fail");
                yield List.of(new WorkOrderFailed(f.reason()));
            }
            case Allocate ignored -> {
                require(state, Status.WAITING_FOR_MATERIAL, "cannot allocate material");
                yield List.of(new MaterialAllocated(state.quantity()));
            }
            case Start s -> {
                require(state, Status.ALLOCATED, "cannot start");
                yield List.of(new WorkOrderStarted(s.smith()));
            }
            case Complete ignored -> {
                require(state, Status.IN_PROGRESS, "cannot complete");
                yield List.of(new WorkOrderCompleted());
            }
        };
    }

    public static WorkOrderState evolve(WorkOrderState state, WorkOrderEvent event) {
        return switch (event) {
            case WorkOrderRequested r ->
                new WorkOrderState(
                        r.workOrderId(), r.orderId(), r.material(), r.quantity(), Status.WAITING_FOR_MATERIAL, null, null);
            case WorkOrderFailed f ->
                new WorkOrderState(
                        state.id(), state.orderId(), state.material(), state.quantity(), Status.FAILED, null, f.reason());
            case MaterialAllocated ignored -> state.with(Status.ALLOCATED);
            case WorkOrderStarted s ->
                new WorkOrderState(
                        state.id(), state.orderId(), state.material(), state.quantity(), Status.IN_PROGRESS, s.smith(), null);
            case WorkOrderCompleted ignored -> state.with(Status.DONE);
        };
    }

    public static WorkOrderState fold(List<? extends WorkOrderEvent> events) {
        var state = WorkOrderState.INITIAL;
        for (var event : events) {
            state = evolve(state, event);
        }
        return state;
    }

    private static void require(WorkOrderState state, Status expected, String what) {
        if (state.status() != expected) {
            throw new WorkOrderRefused(state.id() + ": " + what + " (" + state.status() + ")");
        }
    }
}
