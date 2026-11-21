package de.ibikus.appa.hephaestus.monolith.modular.migration.internal;

import de.ibikus.appa.hephaestus.monolith.modular.forge.ForgeStillBusy;
import de.ibikus.appa.hephaestus.monolith.modular.forge.WorkOrders;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The cutover's control panel (scripts/cutover.sh): state, switch new work orders to forge-service and back
 * (rollback), decommission the local forge once nothing is open.
 */
@RestController
class MigrationAdminController {

    record Cutover(Boolean remote) {}

    private final JdbcForgeRouting routing;
    private final WorkOrders workOrders;

    MigrationAdminController(JdbcForgeRouting routing, WorkOrders workOrders) {
        this.routing = routing;
        this.workOrders = workOrders;
    }

    @GetMapping("/admin/forge")
    Map<String, Object> state() {
        return Map.of(
                "remote", routing.remote(),
                "decommissioned", workOrders.decommissioned(),
                "openLocalWorkOrders", workOrders.open().size());
    }

    @PostMapping("/admin/forge/cutover")
    ResponseEntity<Map<String, Object>> cutover(@RequestBody Cutover cutover) {
        if (cutover.remote() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "remote: true or false"));
        }
        if (!cutover.remote() && workOrders.decommissioned()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "the local forge is decommissioned: no way back"));
        }
        routing.remote(cutover.remote());
        return ResponseEntity.ok(state());
    }

    @PostMapping("/admin/forge/decommission")
    ResponseEntity<Map<String, Object>> decommission() {
        if (!routing.remote()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "new work orders still go to the local forge: cut over first"));
        }
        try {
            workOrders.decommission();
        } catch (ForgeStillBusy busy) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("openLocalWorkOrders", busy.openWorkOrders()));
        }
        return ResponseEntity.ok(state());
    }
}
