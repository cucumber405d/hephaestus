package de.ibikus.appa.hephaestus.cdc.forge.application;

import de.ibikus.appa.hephaestus.cdc.forge.domain.WorkOrder.Status;
import java.util.List;
import org.springframework.stereotype.Service;

/** The smith's list: the service's work orders (in 13 it also merged the monolith's during the Strangler Fig). */
@Service
public class WorkOrderList {

    private final WorkOrders workOrders;

    public WorkOrderList(WorkOrders workOrders) {
        this.workOrders = workOrders;
    }

    public List<ListedWorkOrder> list(Status status) {
        return workOrders.list(status).stream()
                .map(wo -> new ListedWorkOrder(
                        wo.id(),
                        wo.orderId(),
                        wo.material(),
                        wo.quantity(),
                        wo.status().name(),
                        "SERVICE"))
                .toList();
    }
}
