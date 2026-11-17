package de.ibikus.appa.hephaestus.security.ordering.application.port.in;

import de.ibikus.appa.hephaestus.security.ordering.domain.Order;
import de.ibikus.appa.hephaestus.security.ordering.domain.OrderLine;
import java.util.List;

public interface PlaceOrderUseCase {

    Order place(List<OrderLine> lines);
}
