package de.ibikus.appa.hephaestus.eventskafka.ordering.adapter.in.web;

import de.ibikus.appa.hephaestus.eventskafka.ordering.application.port.in.RepublishOrderEventUseCase;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Experiment controls, not part of the shop API: re-send an order's event to provoke a duplicate. */
@RestController
@RequestMapping("/admin")
class AdminController {

    private final RepublishOrderEventUseCase republish;

    AdminController(RepublishOrderEventUseCase republish) {
        this.republish = republish;
    }

    @PostMapping("/republish/{orderId}")
    ResponseEntity<Republished> republish(@PathVariable UUID orderId) {
        var order = republish.republish(orderId);
        return ResponseEntity.accepted().body(new Republished(order.id(), order.eventId()));
    }

    record Republished(UUID orderId, UUID eventId) {}
}
