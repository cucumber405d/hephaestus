package de.ibikus.appa.hephaestus.outboxsaga.ordering.orders;

import de.ibikus.appa.hephaestus.outboxsaga.ordering.messaging.SagaMessage.Line;
import de.ibikus.appa.hephaestus.outboxsaga.ordering.orders.Orders.HistoryEntry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Placing an order starts its saga and answers 202 at once: the outcome comes later, asynchronously. Poll
 * GET /api/orders/{id} for the status and the saga's history.
 */
@RestController
class OrdersController {

    private final OrderService service;
    private final Orders orders;

    OrdersController(OrderService service, Orders orders) {
        this.service = service;
        this.orders = orders;
    }

    @PostMapping("/api/orders")
    ResponseEntity<Placed> place(@Valid @RequestBody PlaceOrder request) {
        var order = service.place(
                request.customerId(),
                request.saga(),
                request.lines().stream()
                        .map(l -> new Line(l.sku(), l.quantity()))
                        .toList());
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(order.id())
                .toUri();
        return ResponseEntity.accepted()
                .location(location)
                .body(new Placed(order.id(), order.status(), order.amount()));
    }

    @GetMapping("/api/orders/{id}")
    OrderView get(@PathVariable UUID id) {
        var order = orders.find(id).orElseThrow(() -> new OrderNotFoundException(id));
        return new OrderView(
                order.id(),
                order.customerId(),
                order.saga(),
                order.status(),
                order.reason(),
                order.amount(),
                order.lines(),
                orders.historyOf(id));
    }

    @PostMapping("/admin/republish/{id}")
    ResponseEntity<Void> republish(@PathVariable UUID id) {
        service.republish(id);
        return ResponseEntity.accepted().build();
    }

    @ExceptionHandler(OrderNotFoundException.class)
    ProblemDetail notFound(OrderNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalid(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    record PlaceOrder(
            @NotNull @Pattern(regexp = "[A-Za-z0-9-]{1,64}") String customerId,

            @NotNull @Pattern(regexp = "choreography|orchestration")
            String saga,

            @NotEmpty List<@Valid LineRequest> lines) {}

    record LineRequest(@NotNull String sku, @Min(1) int quantity) {}

    record Placed(UUID id, String status, BigDecimal amount) {}

    record OrderView(
            UUID id,
            String customerId,
            String saga,
            String status,
            String reason,
            BigDecimal amount,
            List<Line> lines,
            List<HistoryEntry> history) {}
}
