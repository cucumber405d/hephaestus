package de.ibikus.appa.hephaestus.security.ordering.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface OrderSpringDataRepository extends JpaRepository<OrderJpaEntity, UUID> {}
