package de.ibikus.appa.hephaestus.eventskafka.ordering.application.port.in;

import de.ibikus.appa.hephaestus.eventskafka.ordering.domain.Order;
import java.util.UUID;

public interface OrderQuery {

    Order get(UUID id);
}
