package de.ibikus.appa.hephaestus.eventskafka.ordering.adapter.out.persistence;

import de.ibikus.appa.hephaestus.eventskafka.ordering.application.port.out.OrderRepository;
import de.ibikus.appa.hephaestus.eventskafka.ordering.domain.Order;
import de.ibikus.appa.hephaestus.eventskafka.ordering.domain.OrderLine;
import de.ibikus.appa.hephaestus.eventskafka.ordering.domain.OrderStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class JpaOrderRepository implements OrderRepository {

    private final OrderSpringDataRepository repository;

    JpaOrderRepository(OrderSpringDataRepository repository) {
        this.repository = repository;
    }

    /** saveAndFlush runs in its own short transaction (Spring Data's default): only the insert holds a connection. */
    @Override
    public void insert(Order order) {
        repository.saveAndFlush(new OrderJpaEntity(
                order.id(),
                order.customerId(),
                order.eventId(),
                order.status().name(),
                order.reservationId(),
                order.createdAt(),
                order.lines().stream()
                        .map(line -> new OrderLineEmbeddable(line.sku(), line.quantity()))
                        .toList()));
    }

    @Override
    public Optional<Order> findById(UUID id) {
        return repository
                .findById(id)
                .map(entity -> new Order(
                        entity.getId(),
                        entity.getCustomerId(),
                        entity.getEventId(),
                        OrderStatus.valueOf(entity.getStatus()),
                        entity.getLines().stream()
                                .map(line -> new OrderLine(line.getSku(), line.getQuantity()))
                                .toList(),
                        entity.getReservationId(),
                        entity.getCreatedAt()));
    }
}
