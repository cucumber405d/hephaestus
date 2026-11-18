package de.ibikus.appa.hephaestus.security.inventory.application.fakes;

import de.ibikus.appa.hephaestus.security.inventory.application.port.out.StockRepository;
import de.ibikus.appa.hephaestus.security.inventory.domain.StockItem;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory stock, pre-filled with the same items as the V2 seed migration. */
public class InMemoryStockRepository implements StockRepository {

    private final Map<String, StockItem> items = new ConcurrentHashMap<>();

    public InMemoryStockRepository() {
        put(new StockItem("ANVIL-01", "Anvil", 10, 0));
        put(new StockItem("HAMMER-01", "Smithing hammer", 25, 0));
        put(new StockItem("SWORD-01", "Bronze sword", 2, 0));
        put(new StockItem("TONGS-01", "Tongs", 0, 0));
    }

    public void put(StockItem item) {
        items.put(item.sku(), item);
    }

    @Override
    public Optional<StockItem> findBySku(String sku) {
        return Optional.ofNullable(items.get(sku));
    }

    @Override
    public List<StockItem> lockBySkus(Collection<String> skus) {
        return skus.stream()
                .map(items::get)
                .filter(item -> item != null)
                .sorted(Comparator.comparing(StockItem::sku))
                .toList();
    }

    @Override
    public void updateReserved(Collection<StockItem> updated) {
        updated.forEach(this::put);
    }
}
