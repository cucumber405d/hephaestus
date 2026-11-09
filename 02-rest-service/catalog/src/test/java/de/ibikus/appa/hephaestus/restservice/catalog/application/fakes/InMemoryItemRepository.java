package de.ibikus.appa.hephaestus.restservice.catalog.application.fakes;

import de.ibikus.appa.hephaestus.restservice.catalog.application.port.out.ItemRepository;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.CategoryId;
import java.util.HashSet;
import java.util.Set;

/** In-memory item store; grows with the item use cases. */
public class InMemoryItemRepository implements ItemRepository {

    private final Set<CategoryId> categoriesInUse = new HashSet<>();

    public void clear() {
        categoriesInUse.clear();
    }

    public void markCategoryInUse(CategoryId categoryId) {
        categoriesInUse.add(categoryId);
    }

    @Override
    public boolean existsByCategory(CategoryId categoryId) {
        return categoriesInUse.contains(categoryId);
    }
}
