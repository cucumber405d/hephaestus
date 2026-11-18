package de.ibikus.appa.hephaestus.resilience.inventory.application.fakes;

import de.ibikus.appa.hephaestus.resilience.inventory.application.port.out.ReservationRepository;
import de.ibikus.appa.hephaestus.resilience.inventory.domain.Reservation;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryReservationRepository implements ReservationRepository {

    private final Map<UUID, Reservation> byOrder = new ConcurrentHashMap<>();

    @Override
    public Optional<Reservation> findByOrderId(UUID orderId) {
        return Optional.ofNullable(byOrder.get(orderId));
    }

    @Override
    public void insert(Reservation reservation) {
        byOrder.put(reservation.orderId(), reservation);
    }

    public int size() {
        return byOrder.size();
    }
}
