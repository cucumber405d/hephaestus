package de.ibikus.appa.hephaestus.servicecommunication.inventory.application.port.in;

import de.ibikus.appa.hephaestus.servicecommunication.inventory.domain.ReservationLine;
import java.util.List;
import java.util.UUID;

public interface ReserveStockUseCase {

    /** Reserves all lines or nothing; idempotent per order id. */
    ReservationResult reserve(UUID orderId, List<ReservationLine> lines);
}
