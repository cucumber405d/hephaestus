package de.ibikus.appa.hephaestus.eventskafka.ordering.application.service;

import de.ibikus.appa.hephaestus.eventskafka.ordering.application.port.in.AvailabilityQuery;
import de.ibikus.appa.hephaestus.eventskafka.ordering.application.port.in.OrderQuery;
import de.ibikus.appa.hephaestus.eventskafka.ordering.application.port.in.PlaceOrderUseCase;
import de.ibikus.appa.hephaestus.eventskafka.ordering.application.port.in.RepublishOrderEventUseCase;
import de.ibikus.appa.hephaestus.eventskafka.ordering.application.port.out.InventoryPort;
import de.ibikus.appa.hephaestus.eventskafka.ordering.application.port.out.OrderEvents;
import de.ibikus.appa.hephaestus.eventskafka.ordering.application.port.out.OrderRepository;
import de.ibikus.appa.hephaestus.eventskafka.ordering.domain.Order;
import de.ibikus.appa.hephaestus.eventskafka.ordering.domain.OrderLine;
import de.ibikus.appa.hephaestus.eventskafka.ordering.domain.OrderNotFoundException;
import de.ibikus.appa.hephaestus.eventskafka.ordering.domain.OrderStatus;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService implements PlaceOrderUseCase, OrderQuery, AvailabilityQuery, RepublishOrderEventUseCase {

    private final InventoryPort inventory;
    private final OrderRepository orders;
    private final OrderEvents events;
    private final Clock clock;

    public OrderService(InventoryPort inventory, OrderRepository orders, OrderEvents events, Clock clock) {
        this.inventory = inventory;
        this.orders = orders;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Deliberately not @Transactional: the remote call must not hold a database transaction (and a pooled
     * connection) open while waiting for Inventory. If saving fails after a successful reservation, the
     * reservation is orphaned; releasing it reliably is what the outbox and sagas of subproject 10 are for.
     */
    @Override
    public Order place(String customerId, List<OrderLine> lines) {
        Order.requireValidLines(lines);
        // The same SKU twice counts once, quantities added up (Inventory does the same; one line per SKU here)
        Map<String, Integer> merged = new TreeMap<>();
        lines.forEach(line -> merged.merge(line.sku(), line.quantity(), Integer::sum));
        lines = merged.entrySet().stream()
                .map(entry -> new OrderLine(entry.getKey(), entry.getValue()))
                .toList();
        var orderId = UUID.randomUUID();
        var reservationId = inventory.reserve(orderId, lines);
        var order = new Order(
                orderId, customerId, UUID.randomUUID(), OrderStatus.CONFIRMED, lines, reservationId, clock.instant());
        orders.insert(order);
        // Dual write: the order is committed, the event may still fail (then the caller gets 503 and the event is
        // missing). Subproject 10 replaces this with an outbox written in the same transaction as the order.
        events.orderPlaced(order);
        return order;
    }

    @Override
    @Transactional(readOnly = true)
    public Order get(UUID id) {
        return orders.findById(id).orElseThrow(() -> new OrderNotFoundException("Order " + id + " not found"));
    }

    @Override
    public Order republish(UUID orderId) {
        var order = orders.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order " + orderId + " not found"));
        events.orderPlaced(order);
        return order;
    }

    @Override
    public int available(String sku) {
        return inventory.available(sku);
    }
}
