package de.ibikus.appa.hephaestus.resilience.inventory.adapter.in.web;

import de.ibikus.appa.hephaestus.resilience.inventory.application.port.in.Mechanism;
import de.ibikus.appa.hephaestus.resilience.inventory.application.port.in.SupplyAnswer;
import de.ibikus.appa.hephaestus.resilience.inventory.application.port.in.SupplyQuery;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The supplier's offer for a SKU, fetched through one of the three mechanisms: {@code /api/supply/r4j/ANVIL-01},
 * {@code .../sccb/...}, {@code .../spring/...}. {@code source} says whether it is live or the cached last answer.
 */
@RestController
@RequestMapping("/api/supply")
class SupplyController {

    private final SupplyQuery supply;

    SupplyController(SupplyQuery supply) {
        this.supply = supply;
    }

    @GetMapping("/{mechanism}/{sku}")
    SupplyResponse offer(
            @PathVariable String mechanism, @PathVariable @Pattern(regexp = "[A-Z0-9-]{1,40}") String sku) {
        return SupplyResponse.of(supply.offer(Mechanism.fromId(mechanism), sku));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail unknownMechanism(IllegalArgumentException e) {
        return ProblemDetailsAdvice.problem(
                HttpStatus.BAD_REQUEST, "invalid-request", "Invalid request", e.getMessage());
    }

    record SupplyResponse(
            String sku,
            BigDecimal price,
            String currency,
            int leadTimeDays,
            int maxQuantity,
            String source,
            Instant fetchedAt,
            String mechanism) {

        static SupplyResponse of(SupplyAnswer answer) {
            var offer = answer.offer();
            return new SupplyResponse(
                    offer.sku(),
                    offer.price(),
                    offer.currency(),
                    offer.leadTimeDays(),
                    offer.maxQuantity(),
                    answer.source().name().toLowerCase(Locale.ROOT),
                    answer.fetchedAt(),
                    answer.mechanism().id());
        }
    }
}
