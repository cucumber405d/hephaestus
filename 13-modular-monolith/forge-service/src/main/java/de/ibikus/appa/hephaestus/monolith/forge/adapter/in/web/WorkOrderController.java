package de.ibikus.appa.hephaestus.monolith.forge.adapter.in.web;

import de.ibikus.appa.hephaestus.monolith.forge.application.WorkOrders;
import de.ibikus.appa.hephaestus.monolith.forge.domain.WorkOrder.Status;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The smith's API, path for path the monolith's: the Gateway can send a smith to either side. */
@RestController
class WorkOrderController {

    private final WorkOrders workOrders;

    WorkOrderController(WorkOrders workOrders) {
        this.workOrders = workOrders;
    }

    @GetMapping("/api/work-orders")
    List<WorkOrderView> list(@RequestParam(required = false) Status status) {
        return workOrders.list(status).stream().map(WorkOrderView::of).toList();
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
