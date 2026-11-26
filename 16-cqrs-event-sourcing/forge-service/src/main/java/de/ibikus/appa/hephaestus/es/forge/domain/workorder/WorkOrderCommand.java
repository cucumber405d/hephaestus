package de.ibikus.appa.hephaestus.es.forge.domain.workorder;

import java.util.UUID;

/** What may be asked of a work order. */
public sealed interface WorkOrderCommand {

    record Request(UUID orderId, String material, int quantity) implements WorkOrderCommand {}

    record Fail(String reason) implements WorkOrderCommand {}

    record Allocate() implements WorkOrderCommand {}

    record Start(String smith) implements WorkOrderCommand {}

    record Complete() implements WorkOrderCommand {}
}
