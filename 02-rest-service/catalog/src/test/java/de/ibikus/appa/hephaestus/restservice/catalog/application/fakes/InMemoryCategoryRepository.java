package de.ibikus.appa.hephaestus.restservice.catalog.application.fakes;

import de.ibikus.appa.hephaestus.restservice.catalog.application.port.out.CategoryRepository;
import de.ibikus.appa.hephaestus.restservice.catalog.application.port.out.ItemRepository;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.Category;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.CategoryId;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.CategoryInUseException;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.DuplicateCategoryNameException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** In-memory category store with the same constraint behaviour as the database (unique name, FK from items). */
public class InMemoryCategoryRepository implements CategoryRepository {

    private final Map<CategoryId, Category> store = new LinkedHashMap<>();
    private final ItemRepository items;

    public InMemoryCategoryRepository(ItemRepository items) {
        this.items = items;
    }

    /** Spring caches test contexts, so fakes living in a context must be reset between tests. */
    public void clear() {
        store.clear();
    }

    @Override
    public Category insert(Category category) {
        if (existsByNameIgnoreCase(category.name())) {
            throw new DuplicateCategoryNameException("Category '" + category.name() + "' already exists");
        }
        store.put(category.id(), category);
        return category;
    }

    @Override
    public Optional<Category> findById(CategoryId id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Category> findAllOrderedByName() {
        return store.values().stream()
                .sorted(Comparator.comparing(Category::name))
                .toList();
    }

    @Override
    public boolean existsByNameIgnoreCase(String name) {
        return store.values().stream().anyMatch(c -> c.name().equalsIgnoreCase(name));
    }

    @Override
    public void delete(CategoryId id) {
        if (items.existsByCategory(id)) {
            throw new CategoryInUseException("Category " + id.value() + " still has items");
        }
        store.remove(id);
    }
}
