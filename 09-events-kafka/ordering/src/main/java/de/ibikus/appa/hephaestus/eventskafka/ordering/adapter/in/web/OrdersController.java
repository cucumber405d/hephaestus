package de.ibikus.appa.hephaestus.eventskafka.ordering.adapter.in.web;

import de.ibikus.appa.hephaestus.eventskafka.ordering.application.port.in.AvailabilityQuery;
import de.ibikus.appa.hephaestus.eventskafka.ordering.application.port.in.OrderQuery;
import de.ibikus.appa.hephaestus.eventskafka.ordering.application.port.in.PlaceOrderUseCase;
import de.ibikus.appa.hephaestus.eventskafka.ordering.domain.Order;
import de.ibikus.appa.hephaestus.eventskafka.ordering.domain.OrderLine;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/orders")
class OrdersController {

    private final PlaceOrderUseCase placeOrder;
    private final OrderQuery orders;
    private final AvailabilityQuery availability;

    OrdersController(PlaceOrderUseCase placeOrder, OrderQuery orders, AvailabilityQuery availability) {
        this.placeOrder = placeOrder;
        this.orders = orders;
        this.availability = availability;
    }

    @PostMapping
    ResponseEntity<OrderResponse> place(@Valid @RequestBody PlaceOrderRequest request) {
        var order = placeOrder.place(request.lines().stream()
                .map(line -> new OrderLine(line.sku(), line.quantity()))
                .toList());
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(order.id())
                .toUri();
        return ResponseEntity.created(location).body(OrderResponse.of(order));
    }

    @GetMapping("/{id}")
    OrderResponse get(@PathVariable UUID id) {
        return OrderResponse.of(orders.get(id));
    }

    @GetMapping("/availability/{sku}")
    AvailabilityResponse availability(@PathVariable @Pattern(regexp = OrderLine.SKU_PATTERN) String sku) {
        return new AvailabilityResponse(sku, availability.available(sku));
    }

    record PlaceOrderRequest(
            @NotEmpty @Size(max = Order.MAX_LINES) List<@Valid Line> lines) {}

    record Line(
            @NotNull @Pattern(regexp = OrderLine.SKU_PATTERN)
            String sku,

            @Min(1) @Max(OrderLine.MAX_QUANTITY) int quantity) {}

    record OrderResponse(UUID id, String status, UUID reservationId, List<Line> lines, Instant createdAt) {

        static OrderResponse of(Order order) {
            return new OrderResponse(
                    order.id(),
                    order.status().name(),
                    order.reservationId(),
                    order.lines().stream()
                            .map(line -> new Line(line.sku(), line.quantity()))
                            .toList(),
                    order.createdAt());
        }
    }

    record AvailabilityResponse(String sku, int available) {}
}
