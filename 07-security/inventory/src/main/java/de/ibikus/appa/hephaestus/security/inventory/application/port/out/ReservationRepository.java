package de.ibikus.appa.hephaestus.security.inventory.application.port.out;

import de.ibikus.appa.hephaestus.security.inventory.domain.Reservation;
import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository {

    Optional<Reservation> findByOrderId(UUID orderId);

    void insert(Reservation reservation);
}
