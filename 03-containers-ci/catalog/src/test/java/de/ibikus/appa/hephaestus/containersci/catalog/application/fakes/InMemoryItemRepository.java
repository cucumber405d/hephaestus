package de.ibikus.appa.hephaestus.containersci.catalog.application.fakes;

import de.ibikus.appa.hephaestus.containersci.catalog.application.port.out.ItemRepository;
import de.ibikus.appa.hephaestus.containersci.catalog.domain.CategoryId;
import de.ibikus.appa.hephaestus.containersci.catalog.domain.Item;
import de.ibikus.appa.hephaestus.containersci.catalog.domain.ItemId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory ItemRepository for unit and web-slice tests. */
public class InMemoryItemRepository implements ItemRepository {

    private final Map<ItemId, Item> items = new ConcurrentHashMap<>();

    @Override
    public Item insert(Item item) {
        items.put(item.id(), item);
        return item;
    }

    @Override
    public Optional<Item> findById(ItemId id) {
        return Optional.ofNullable(items.get(id));
    }

    @Override
    public List<Item> findAll(Optional<CategoryId> category, int limit) {
        return items.values().stream()
                .filter(item -> category.map(item.categoryId()::equals).orElse(true))
                .sorted(Comparator.comparing(Item::name))
                .limit(limit)
                .toList();
    }
}
