package de.ibikus.appa.hephaestus.monolith.forge.adapter.in.web;

import de.ibikus.appa.hephaestus.monolith.forge.domain.WorkOrder;
import java.util.UUID;

/** The smith's JSON, the same as the monolith's; {@code origin} tells whose work order it is. */
public record WorkOrderView(String id, UUID orderId, String material, int quantity, String status, String origin) {

    static WorkOrderView of(WorkOrder wo) {
        return new WorkOrderView(wo.id(), wo.orderId(), wo.material(), wo.quantity(), wo.status().name(), "SERVICE");
    }
}
