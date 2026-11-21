package de.ibikus.appa.hephaestus.monolith.legacy.forge;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The smith's API: list, start, complete. A repeated transition is 409. */
@RestController
class WorkOrderController {

    private final ForgeService forge;

    WorkOrderController(ForgeService forge) {
        this.forge = forge;
    }

    @GetMapping("/api/work-orders")
    List<WorkOrder> list(@RequestParam(required = false) String status) {
        return forge.list(status);
    }

    @PostMapping("/api/work-orders/{id}/start")
    void start(@PathVariable String id) {
        forge.start(id);
    }

    @PostMapping("/api/work-orders/{id}/complete")
    void complete(@PathVariable String id) {
        forge.complete(id);
    }
}
