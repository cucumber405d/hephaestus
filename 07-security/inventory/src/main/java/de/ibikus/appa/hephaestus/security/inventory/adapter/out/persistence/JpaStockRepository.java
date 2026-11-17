package de.ibikus.appa.hephaestus.security.inventory.adapter.out.persistence;

import de.ibikus.appa.hephaestus.security.inventory.application.port.out.StockRepository;
import de.ibikus.appa.hephaestus.security.inventory.domain.StockItem;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class JpaStockRepository implements StockRepository {

    private final StockItemSpringDataRepository repository;

    JpaStockRepository(StockItemSpringDataRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<StockItem> findBySku(String sku) {
        return repository.findById(sku).map(JpaStockRepository::toDomain);
    }

    @Override
    public List<StockItem> lockBySkus(Collection<String> skus) {
        return repository.findBySkuInOrderBySkuAsc(skus).stream()
                .map(JpaStockRepository::toDomain)
                .toList();
    }

    /** The rows are managed and locked in this transaction; dirty checking writes the new counts on commit. */
    @Override
    public void updateReserved(Collection<StockItem> items) {
        for (var item : items) {
            var entity = repository
                    .findById(item.sku())
                    .orElseThrow(() -> new IllegalStateException("Locked stock row vanished: " + item.sku()));
            entity.setReserved(item.reserved());
        }
        repository.flush();
    }

    private static StockItem toDomain(StockItemJpaEntity entity) {
        return new StockItem(entity.getSku(), entity.getName(), entity.getOnHand(), entity.getReserved());
    }
}
