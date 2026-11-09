package de.ibikus.appa.hephaestus.restservice.catalog.application.fakes;

import de.ibikus.appa.hephaestus.restservice.catalog.application.port.out.ItemRepository;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.CategoryId;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.Item;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.ItemId;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** In-memory item store; grows with the item use cases. */
public class InMemoryItemRepository implements ItemRepository {

    private final Map<ItemId, Item> store = new LinkedHashMap<>();
    private final Set<CategoryId> categoriesInUse = new HashSet<>();

    public void clear() {
        store.clear();
        categoriesInUse.clear();
    }

    public void markCategoryInUse(CategoryId categoryId) {
        categoriesInUse.add(categoryId);
    }

    @Override
    public Item insert(Item item) {
        store.put(item.id(), item);
        return item;
    }

    @Override
    public Optional<Item> findById(ItemId id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public boolean existsByCategory(CategoryId categoryId) {
        return categoriesInUse.contains(categoryId)
                || store.values().stream().anyMatch(item -> item.categoryId().equals(categoryId));
    }
}
