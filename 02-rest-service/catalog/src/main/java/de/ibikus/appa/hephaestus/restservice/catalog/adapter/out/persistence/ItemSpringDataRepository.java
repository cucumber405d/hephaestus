package de.ibikus.appa.hephaestus.restservice.catalog.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ItemSpringDataRepository extends JpaRepository<ItemJpaEntity, UUID> {

    boolean existsByCategoryId(UUID categoryId);
}
