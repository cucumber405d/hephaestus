package de.ibikus.appa.hephaestus.containersci.catalog.application.service;

import de.ibikus.appa.hephaestus.containersci.catalog.application.port.in.ManageItemsUseCase;
import de.ibikus.appa.hephaestus.containersci.catalog.application.port.out.CategoryRepository;
import de.ibikus.appa.hephaestus.containersci.catalog.application.port.out.ItemRepository;
import de.ibikus.appa.hephaestus.containersci.catalog.domain.CategoryId;
import de.ibikus.appa.hephaestus.containersci.catalog.domain.Item;
import de.ibikus.appa.hephaestus.containersci.catalog.domain.ItemId;
import de.ibikus.appa.hephaestus.containersci.catalog.domain.ItemNotFoundException;
import de.ibikus.appa.hephaestus.containersci.catalog.domain.NewItem;
import de.ibikus.appa.hephaestus.containersci.catalog.domain.UnknownCategoryException;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ItemService implements ManageItemsUseCase {

    /** Upper bound of a list response; this slim service has no pagination. */
    public static final int LIST_LIMIT = 100;

    private final ItemRepository items;
    private final CategoryRepository categories;
    private final Clock clock;

    public ItemService(ItemRepository items, CategoryRepository categories, Clock clock) {
        this.items = items;
        this.categories = categories;
        this.clock = clock;
    }

    /** The category check gives a clear 422; the FK still catches a category deleted in between. */
    @Override
    @Transactional
    public Item create(NewItem data) {
        if (!categories.exists(data.categoryId())) {
            throw new UnknownCategoryException("Category " + data.categoryId().value() + " does not exist");
        }
        return items.insert(Item.create(data, clock.instant()));
    }

    @Override
    @Transactional(readOnly = true)
    public Item get(ItemId id) {
        return items.findById(id).orElseThrow(() -> new ItemNotFoundException("Item " + id.value() + " not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Item> list(Optional<CategoryId> category) {
        return items.findAll(category, LIST_LIMIT);
    }
}
