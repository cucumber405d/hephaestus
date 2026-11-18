package de.ibikus.appa.hephaestus.eventskafka.ordering.application.port.in;

import de.ibikus.appa.hephaestus.eventskafka.ordering.domain.Order;
import java.util.UUID;

/** For experiments: send the OrderPlaced event of an existing order again, with the same event id (a duplicate). */
public interface RepublishOrderEventUseCase {

    Order republish(UUID orderId);
}
