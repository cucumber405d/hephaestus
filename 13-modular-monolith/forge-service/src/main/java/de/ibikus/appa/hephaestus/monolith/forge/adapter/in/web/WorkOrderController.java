package de.ibikus.appa.hephaestus.monolith.forge.adapter.in.web;

import de.ibikus.appa.hephaestus.monolith.forge.application.ListedWorkOrder;
import de.ibikus.appa.hephaestus.monolith.forge.application.WorkOrderList;
import de.ibikus.appa.hephaestus.monolith.forge.application.WorkOrders;
import de.ibikus.appa.hephaestus.monolith.forge.domain.WorkOrder.Status;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The smith's API, path for path the monolith's: the Gateway can send a smith to either side. The list is the
 * Strangler Fig facade (own + the monolith's open work orders); {@code X-Partial: monolith-unavailable} when the
 * monolith did not answer.
 */
@RestController
class WorkOrderController {

    private final WorkOrders workOrders;
    private final WorkOrderList list;

    WorkOrderController(WorkOrders workOrders, WorkOrderList list) {
        this.workOrders = workOrders;
        this.list = list;
    }

    @GetMapping("/api/work-orders")
    ResponseEntity<List<ListedWorkOrder>> list(@RequestParam(required = false) Status status) {
        var listing = list.list(status);
        var response = ResponseEntity.ok();
        if (listing.partial()) {
            response.header("X-Partial", "monolith-unavailable");
        }
        return response.body(listing.items());
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
