package de.ibikus.appa.hephaestus.restservice.catalog.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface CategorySpringDataRepository extends JpaRepository<CategoryJpaEntity, UUID> {

    List<CategoryJpaEntity> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);
}
