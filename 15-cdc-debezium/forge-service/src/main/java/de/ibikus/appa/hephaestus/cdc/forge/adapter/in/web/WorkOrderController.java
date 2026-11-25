package de.ibikus.appa.hephaestus.cdc.forge.adapter.in.web;

import de.ibikus.appa.hephaestus.cdc.forge.application.ListedWorkOrder;
import de.ibikus.appa.hephaestus.cdc.forge.application.WorkOrderList;
import de.ibikus.appa.hephaestus.cdc.forge.application.WorkOrders;
import de.ibikus.appa.hephaestus.cdc.forge.domain.WorkOrder.Status;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The smith's API, as in 13: list, start, complete. */
@RestController
class WorkOrderController {

    private final WorkOrders workOrders;
    private final WorkOrderList list;

    WorkOrderController(WorkOrders workOrders, WorkOrderList list) {
        this.workOrders = workOrders;
        this.list = list;
    }

    @GetMapping("/api/work-orders")
    List<ListedWorkOrder> list(@RequestParam(required = false) Status status) {
        return list.list(status);
    }

    @PostMapping("/api/work-orders/{id}/start")
    void start(@PathVariable String id) {
        workOrders.start(id);
    }

    @PostMapping("/api/work-orders/{id}/complete")
    void complete(@PathVariable String id) {
        workOrders.complete(id);
    }
}
