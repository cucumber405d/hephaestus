package de.ibikus.appa.hephaestus.security.ordering.application.port.in;

import de.ibikus.appa.hephaestus.security.ordering.domain.Order;
import java.util.UUID;

public interface OrderQuery {

    Order get(UUID id);
}
