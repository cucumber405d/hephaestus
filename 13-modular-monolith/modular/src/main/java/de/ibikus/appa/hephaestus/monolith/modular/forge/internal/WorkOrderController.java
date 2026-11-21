package de.ibikus.appa.hephaestus.monolith.modular.forge.internal;

import de.ibikus.appa.hephaestus.monolith.modular.forge.WorkOrder;
import de.ibikus.appa.hephaestus.monolith.modular.forge.WorkOrders;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The smith's API, unchanged from the legacy: list, start, complete; a repeated transition is 409. */
@RestController
class WorkOrderController {

    private final WorkOrders workOrders;

    WorkOrderController(WorkOrders workOrders) {
        this.workOrders = workOrders;
    }

    @GetMapping("/api/work-orders")
    List<WorkOrder> list(@RequestParam(required = false) String status) {
        return workOrders.list(status);
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
