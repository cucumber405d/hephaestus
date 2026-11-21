package de.ibikus.appa.hephaestus.monolith.modular.forge.internal;

import de.ibikus.appa.hephaestus.monolith.modular.forge.WorkOrder;
import de.ibikus.appa.hephaestus.monolith.modular.forge.WorkOrders;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The smith's API, unchanged from the legacy: list, start, complete; a repeated transition is 409. Gone (410) once the
 * local forge is decommissioned. {@code /internal/work-orders} serves forge-service's facade list.
 */
@RestController
class WorkOrderController {

    private final WorkOrders workOrders;

    WorkOrderController(WorkOrders workOrders) {
        this.workOrders = workOrders;
    }

    @GetMapping("/api/work-orders")
    List<WorkOrder> list(@RequestParam(required = false) String status) {
        stillHere();
        return workOrders.list(status);
    }

    @PostMapping("/api/work-orders/{id}/start")
    void start(@PathVariable String id) {
        stillHere();
        workOrders.start(id);
    }

    @PostMapping("/api/work-orders/{id}/complete")
    void complete(@PathVariable String id) {
        stillHere();
        workOrders.complete(id);
    }

    /** The open local work orders, for forge-service's list during the transition; empty once decommissioned. */
    @GetMapping("/internal/work-orders")
    List<WorkOrder> open() {
        return workOrders.decommissioned() ? List.of() : workOrders.open();
    }

    private void stillHere() {
        if (workOrders.decommissioned()) {
            throw new ResponseStatusException(HttpStatus.GONE, "the local forge is decommissioned");
        }
    }
}
