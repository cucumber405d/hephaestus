package de.ibikus.appa.hephaestus.resilience.inventory.adapter.out.persistence;

import de.ibikus.appa.hephaestus.resilience.inventory.application.port.out.ReservationRepository;
import de.ibikus.appa.hephaestus.resilience.inventory.domain.Reservation;
import de.ibikus.appa.hephaestus.resilience.inventory.domain.ReservationLine;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class JpaReservationRepository implements ReservationRepository {

    private final ReservationSpringDataRepository repository;

    JpaReservationRepository(ReservationSpringDataRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Reservation> findByOrderId(UUID orderId) {
        return repository.findByOrderId(orderId).map(JpaReservationRepository::toDomain);
    }

    @Override
    public void insert(Reservation reservation) {
        repository.saveAndFlush(new ReservationJpaEntity(
                reservation.id(),
                reservation.orderId(),
                reservation.createdAt(),
                reservation.lines().stream()
                        .map(line -> new ReservationLineEmbeddable(line.sku(), line.quantity()))
                        .toList()));
    }

    private static Reservation toDomain(ReservationJpaEntity entity) {
        return new Reservation(
                entity.getId(),
                entity.getOrderId(),
                entity.getLines().stream()
                        .map(line -> new ReservationLine(line.getSku(), line.getQuantity()))
                        .toList(),
                entity.getCreatedAt());
    }
}
