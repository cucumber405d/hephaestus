package de.ibikus.appa.hephaestus.resilience.supplier;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** The supplier's public API: one offer per SKU from a fixed catalog, subject to the configured faults. */
@RestController
class OffersController {

    static final Map<String, Offer> CATALOG = Map.of(
            "ANVIL-01", new Offer("ANVIL-01", new BigDecimal("120.00"), "EUR", 5, 20),
            "HAMMER-01", new Offer("HAMMER-01", new BigDecimal("35.00"), "EUR", 3, 100),
            "TONGS-01", new Offer("TONGS-01", new BigDecimal("18.50"), "EUR", 7, 50));

    private final SupplierState state;

    OffersController(SupplierState state) {
        this.state = state;
    }

    @GetMapping("/api/offers/{sku}")
    ResponseEntity<?> offer(@PathVariable String sku) throws InterruptedException {
        state.countOfferCall();
        var faults = state.faults();
        if (faults.delayMs() > 0) {
            Thread.sleep(faults.delayMs());
        }
        if (faults.failsNow()) {
            var problem = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(faults.status()), "Injected fault");
            return ResponseEntity.status(faults.status()).body(problem);
        }
        var offer = CATALOG.get(sku);
        if (offer == null) {
            var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "No offer for " + sku);
            problem.setType(URI.create("https://supplier.example/problems/unknown-sku"));
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
        }
        return ResponseEntity.ok(offer);
    }
}
