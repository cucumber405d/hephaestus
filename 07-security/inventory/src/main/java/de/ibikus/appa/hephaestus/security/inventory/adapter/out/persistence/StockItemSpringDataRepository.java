package de.ibikus.appa.hephaestus.security.inventory.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

interface StockItemSpringDataRepository extends JpaRepository<StockItemJpaEntity, String> {

    /** SELECT ... ORDER BY sku FOR UPDATE: rows locked in a fixed order. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<StockItemJpaEntity> findBySkuInOrderBySkuAsc(Collection<String> skus);
}
