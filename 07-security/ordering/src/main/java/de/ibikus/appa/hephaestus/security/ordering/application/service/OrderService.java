package de.ibikus.appa.hephaestus.security.ordering.application.service;

import de.ibikus.appa.hephaestus.security.ordering.application.port.in.AvailabilityQuery;
import de.ibikus.appa.hephaestus.security.ordering.application.port.in.OrderQuery;
import de.ibikus.appa.hephaestus.security.ordering.application.port.in.PlaceOrderUseCase;
import de.ibikus.appa.hephaestus.security.ordering.application.port.out.InventoryPort;
import de.ibikus.appa.hephaestus.security.ordering.application.port.out.OrderRepository;
import de.ibikus.appa.hephaestus.security.ordering.domain.Order;
import de.ibikus.appa.hephaestus.security.ordering.domain.OrderLine;
import de.ibikus.appa.hephaestus.security.ordering.domain.OrderNotFoundException;
import de.ibikus.appa.hephaestus.security.ordering.domain.OrderStatus;
import de.ibikus.appa.hephaestus.security.ordering.domain.Requester;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService implements PlaceOrderUseCase, OrderQuery, AvailabilityQuery {

    private final InventoryPort inventory;
    private final OrderRepository orders;
    private final Clock clock;

    public OrderService(InventoryPort inventory, OrderRepository orders, Clock clock) {
        this.inventory = inventory;
        this.orders = orders;
        this.clock = clock;
    }

    /**
     * Deliberately not @Transactional: the remote call must not hold a database transaction (and a pooled
     * connection) open while waiting for Inventory. If saving fails after a successful reservation, the
     * reservation is orphaned; releasing it reliably is what the outbox and sagas of subproject 10 are for.
     */
    @Override
    public Order place(String customerId, List<OrderLine> lines) {
        // Validate before reserving anything: a rejected order must not leave a reservation behind
        Order.requireValidCustomerId(customerId);
        Order.requireValidLines(lines);
        // The same SKU twice counts once, quantities added up (Inventory does the same; one line per SKU here)
        Map<String, Integer> merged = new TreeMap<>();
        lines.forEach(line -> merged.merge(line.sku(), line.quantity(), Integer::sum));
        lines = merged.entrySet().stream()
                .map(entry -> new OrderLine(entry.getKey(), entry.getValue()))
                .toList();
        var orderId = UUID.randomUUID();
        var reservationId = inventory.reserve(orderId, lines);
        var order = new Order(orderId, customerId, OrderStatus.CONFIRMED, lines, reservationId, clock.instant());
        orders.insert(order);
        return order;
    }

    @Override
    @Transactional(readOnly = true)
    public Order get(UUID id, Requester requester) {
        return orders.findById(id)
                .filter(requester::mayRead)
                .orElseThrow(() -> new OrderNotFoundException("Order " + id + " not found"));
    }

    @Override
    public int available(String sku) {
        return inventory.available(sku);
    }
}
