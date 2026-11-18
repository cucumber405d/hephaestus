package de.ibikus.appa.hephaestus.outboxsaga.inventory.admin;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Experiment controls of this service (bound to localhost in Compose): faults and counters. */
@RestController
@RequestMapping("/admin")
class AdminController {

    private final Levers levers;

    AdminController(Levers levers) {
        this.levers = levers;
    }

    @PutMapping("/faults")
    Faults setFaults(@RequestBody Faults faults) {
        levers.faults(faults);
        return faults;
    }

    @DeleteMapping("/faults")
    ResponseEntity<Void> clearFaults() {
        levers.faults(Faults.NONE);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/stats")
    Map<String, Long> stats() {
        return Map.of("handled", levers.handledCount(), "duplicates", levers.duplicateCount());
    }
}
