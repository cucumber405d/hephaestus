package de.ibikus.appa.hephaestus.eventskafka.ordering.application.fakes;

import de.ibikus.appa.hephaestus.eventskafka.ordering.application.port.out.OrderRepository;
import de.ibikus.appa.hephaestus.eventskafka.ordering.domain.Order;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryOrderRepository implements OrderRepository {

    private final Map<UUID, Order> orders = new ConcurrentHashMap<>();

    @Override
    public void insert(Order order) {
        orders.put(order.id(), order);
    }

    @Override
    public Optional<Order> findById(UUID id) {
        return Optional.ofNullable(orders.get(id));
    }

    public int size() {
        return orders.size();
    }
}
