package de.ibikus.appa.hephaestus.resilience.inventory.application.port.out;

import de.ibikus.appa.hephaestus.resilience.inventory.domain.StockItem;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StockRepository {

    Optional<StockItem> findBySku(String sku);

    /**
     * Loads and locks the stock rows of these SKUs for the current transaction, always in SKU order: two
     * reservations touching the same SKUs wait for each other instead of deadlocking. Unknown SKUs are absent.
     */
    List<StockItem> lockBySkus(Collection<String> skus);

    void updateReserved(Collection<StockItem> items);
}
