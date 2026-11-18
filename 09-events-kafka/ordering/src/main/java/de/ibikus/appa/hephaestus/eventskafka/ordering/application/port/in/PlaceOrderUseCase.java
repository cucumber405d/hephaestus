package de.ibikus.appa.hephaestus.eventskafka.ordering.application.port.in;

import de.ibikus.appa.hephaestus.eventskafka.ordering.domain.Order;
import de.ibikus.appa.hephaestus.eventskafka.ordering.domain.OrderLine;
import java.util.List;

public interface PlaceOrderUseCase {

    /** Stores the order, then publishes OrderPlaced (keyed by the customer). */
    Order place(String customerId, List<OrderLine> lines);
}
