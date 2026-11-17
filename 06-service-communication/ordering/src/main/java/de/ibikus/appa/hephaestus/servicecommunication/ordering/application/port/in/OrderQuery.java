package de.ibikus.appa.hephaestus.servicecommunication.ordering.application.port.in;

import de.ibikus.appa.hephaestus.servicecommunication.ordering.domain.Order;
import java.util.UUID;

public interface OrderQuery {

    Order get(UUID id);
}
