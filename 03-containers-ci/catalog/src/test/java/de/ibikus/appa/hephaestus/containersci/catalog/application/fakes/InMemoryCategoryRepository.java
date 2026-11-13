package de.ibikus.appa.hephaestus.containersci.catalog.application.fakes;

import de.ibikus.appa.hephaestus.containersci.catalog.application.port.out.CategoryRepository;
import de.ibikus.appa.hephaestus.containersci.catalog.domain.Category;
import de.ibikus.appa.hephaestus.containersci.catalog.domain.CategoryId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory CategoryRepository, pre-filled with the same three categories the V3 migration seeds. */
public class InMemoryCategoryRepository implements CategoryRepository {

    public static final CategoryId ARMOR = new CategoryId(UUID.fromString("01900000-0000-7000-8000-000000000001"));
    public static final CategoryId TOOLS = new CategoryId(UUID.fromString("01900000-0000-7000-8000-000000000002"));
    public static final CategoryId WEAPONS = new CategoryId(UUID.fromString("01900000-0000-7000-8000-000000000003"));

    private final Map<CategoryId, Category> categories = new ConcurrentHashMap<>(Map.of(
            WEAPONS, new Category(WEAPONS, "Weapons"),
            ARMOR, new Category(ARMOR, "Armor"),
            TOOLS, new Category(TOOLS, "Tools")));

    @Override
    public boolean exists(CategoryId id) {
        return categories.containsKey(id);
    }

    @Override
    public List<Category> findAllOrderedByName() {
        return categories.values().stream()
                .sorted(Comparator.comparing(Category::name))
                .toList();
    }
}
