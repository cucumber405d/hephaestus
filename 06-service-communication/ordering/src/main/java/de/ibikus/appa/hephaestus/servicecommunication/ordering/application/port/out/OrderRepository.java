package de.ibikus.appa.hephaestus.servicecommunication.ordering.application.port.out;

import de.ibikus.appa.hephaestus.servicecommunication.ordering.domain.Order;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository {

    void insert(Order order);

    Optional<Order> findById(UUID id);
}
