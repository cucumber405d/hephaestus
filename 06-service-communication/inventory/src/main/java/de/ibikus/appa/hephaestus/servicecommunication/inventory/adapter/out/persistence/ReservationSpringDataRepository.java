package de.ibikus.appa.hephaestus.servicecommunication.inventory.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ReservationSpringDataRepository extends JpaRepository<ReservationJpaEntity, UUID> {

    Optional<ReservationJpaEntity> findByOrderId(UUID orderId);
}
