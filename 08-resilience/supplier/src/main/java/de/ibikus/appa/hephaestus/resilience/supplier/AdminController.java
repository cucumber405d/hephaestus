package de.ibikus.appa.hephaestus.resilience.supplier;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Test controls: switch HTTP faults on and off, read and reset the call counter. Not part of the "real" API. */
@RestController
@RequestMapping("/admin")
class AdminController {

    private final SupplierState state;

    AdminController(SupplierState state) {
        this.state = state;
    }

    @PutMapping("/faults")
    Faults setFaults(@RequestBody Faults faults) {
        state.faults(faults);
        return faults;
    }

    @DeleteMapping("/faults")
    ResponseEntity<Void> clearFaults() {
        state.faults(Faults.NONE);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/stats")
    Stats stats() {
        return new Stats(state.offerCalls());
    }

    @DeleteMapping("/stats")
    ResponseEntity<Void> resetStats() {
        state.resetCalls();
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<String> invalid(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }

    record Stats(long offerCalls) {}
}
