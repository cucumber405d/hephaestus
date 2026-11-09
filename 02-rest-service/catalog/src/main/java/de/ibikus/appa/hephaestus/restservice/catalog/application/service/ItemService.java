package de.ibikus.appa.hephaestus.restservice.catalog.application.service;

import de.ibikus.appa.hephaestus.restservice.catalog.application.port.in.ManageItemsUseCase;
import de.ibikus.appa.hephaestus.restservice.catalog.application.port.in.SearchItemsQuery;
import de.ibikus.appa.hephaestus.restservice.catalog.application.port.out.CategoryRepository;
import de.ibikus.appa.hephaestus.restservice.catalog.application.port.out.ItemRepository;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.Item;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.ItemId;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.ItemNotFoundException;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.ItemSearchCriteria;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.NewItem;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.Page;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.PageRequest;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.UnknownCategoryException;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Item use cases. */
@Service
@Transactional
public class ItemService implements ManageItemsUseCase, SearchItemsQuery {

    private final ItemRepository items;
    private final CategoryRepository categories;
    private final Clock clock;

    public ItemService(ItemRepository items, CategoryRepository categories, Clock clock) {
        this.items = items;
        this.categories = categories;
        this.clock = clock;
    }

    @Override
    public Item create(NewItem data) {
        if (categories.findById(data.categoryId()).isEmpty()) {
            throw new UnknownCategoryException("Category " + data.categoryId().value() + " does not exist");
        }
        // PostgreSQL timestamptz keeps microseconds: truncate so POST and a later GET return the same instant
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        return items.insert(Item.create(data, now));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Item> search(ItemSearchCriteria criteria, PageRequest pageRequest) {
        return items.search(criteria, pageRequest);
    }

    @Override
    @Transactional(readOnly = true)
    public Item get(ItemId id) {
        return items.findById(id).orElseThrow(() -> new ItemNotFoundException("Item " + id.value() + " not found"));
    }
}
