package de.ibikus.appa.hephaestus.servicecommunication.ordering.application.port.in;

import de.ibikus.appa.hephaestus.servicecommunication.ordering.domain.Order;
import de.ibikus.appa.hephaestus.servicecommunication.ordering.domain.OrderLine;
import java.util.List;

public interface PlaceOrderUseCase {

    Order place(List<OrderLine> lines);
}
