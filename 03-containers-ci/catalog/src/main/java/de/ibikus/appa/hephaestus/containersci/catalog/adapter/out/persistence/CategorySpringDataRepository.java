package de.ibikus.appa.hephaestus.containersci.catalog.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface CategorySpringDataRepository extends JpaRepository<CategoryJpaEntity, UUID> {

    List<CategoryJpaEntity> findAllByOrderByNameAsc();
}
