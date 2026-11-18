package de.ibikus.appa.hephaestus.security.ordering.application.port.in;

import de.ibikus.appa.hephaestus.security.ordering.domain.Order;
import de.ibikus.appa.hephaestus.security.ordering.domain.OrderLine;
import java.util.List;

public interface PlaceOrderUseCase {

    /** Places an order for the customer identified by the token's subject. */
    Order place(String customerId, List<OrderLine> lines);
}
