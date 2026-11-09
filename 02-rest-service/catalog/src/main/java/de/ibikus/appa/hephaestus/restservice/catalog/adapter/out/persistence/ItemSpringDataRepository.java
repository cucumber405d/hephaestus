package de.ibikus.appa.hephaestus.restservice.catalog.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface ItemSpringDataRepository extends JpaRepository<ItemJpaEntity, UUID>, JpaSpecificationExecutor<ItemJpaEntity> {

    boolean existsByCategoryId(UUID categoryId);
}
