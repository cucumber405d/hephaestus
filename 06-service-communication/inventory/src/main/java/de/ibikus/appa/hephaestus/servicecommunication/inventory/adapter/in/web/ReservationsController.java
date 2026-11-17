package de.ibikus.appa.hephaestus.servicecommunication.inventory.adapter.in.web;

import de.ibikus.appa.hephaestus.servicecommunication.inventory.application.port.in.ReserveStockUseCase;
import de.ibikus.appa.hephaestus.servicecommunication.inventory.domain.Reservation;
import de.ibikus.appa.hephaestus.servicecommunication.inventory.domain.ReservationLine;
import de.ibikus.appa.hephaestus.servicecommunication.inventory.domain.Skus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Internal API for Ordering: not routed by the Gateway. Idempotent per orderId: a repeat returns 200 with the
 * reservation made by the first request, so a caller may safely retry after a timeout.
 */
@RestController
@RequestMapping("/api/reservations")
class ReservationsController {

    private final ReserveStockUseCase reservations;

    ReservationsController(ReserveStockUseCase reservations) {
        this.reservations = reservations;
    }

    @PostMapping
    ResponseEntity<ReservationResponse> reserve(@Valid @RequestBody ReservationRequest request) {
        var result = reservations.reserve(
                request.orderId(),
                request.lines().stream()
                        .map(line -> new ReservationLine(line.sku(), line.quantity()))
                        .toList());
        var body = ReservationResponse.of(result.reservation());
        if (!result.created()) {
            return ResponseEntity.ok(body);
        }
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(body.id())
                .toUri();
        return ResponseEntity.created(location).body(body);
    }

    record ReservationRequest(
            @NotNull UUID orderId, @NotEmpty @Size(max = Reservation.MAX_LINES) List<@Valid Line> lines) {}

    record Line(
            @NotNull @Pattern(regexp = Skus.PATTERN) String sku,
            @Min(1) @Max(ReservationLine.MAX_QUANTITY) int quantity) {}

    record ReservationResponse(UUID id, UUID orderId, List<Line> lines, Instant createdAt) {

        static ReservationResponse of(Reservation reservation) {
            return new ReservationResponse(
                    reservation.id(),
                    reservation.orderId(),
                    reservation.lines().stream()
                            .map(line -> new Line(line.sku(), line.quantity()))
                            .toList(),
                    reservation.createdAt());
        }
    }
}
