package de.ibikus.appa.hephaestus.containersci.catalog.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

interface ItemSpringDataRepository extends JpaRepository<ItemJpaEntity, UUID> {

    List<ItemJpaEntity> findAllByOrderByNameAsc(Limit limit);

    List<ItemJpaEntity> findByCategoryIdOrderByNameAsc(UUID categoryId, Limit limit);
}
