package de.ibikus.appa.hephaestus.restservice.catalog.application.service;

import de.ibikus.appa.hephaestus.restservice.catalog.application.port.in.ItemPatch;
import de.ibikus.appa.hephaestus.restservice.catalog.application.port.in.ManageItemsUseCase;
import de.ibikus.appa.hephaestus.restservice.catalog.application.port.in.PatchValue;
import de.ibikus.appa.hephaestus.restservice.catalog.application.port.in.SearchItemsQuery;
import de.ibikus.appa.hephaestus.restservice.catalog.application.port.out.CategoryRepository;
import de.ibikus.appa.hephaestus.restservice.catalog.application.port.out.ItemRepository;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.Item;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.ItemId;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.ItemNotFoundException;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.ItemSearchCriteria;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.Money;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.NewItem;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.Page;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.PageRequest;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.UnknownCategoryException;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.VersionConflictException;
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
        requireCategory(data);
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
    public Item replace(ItemId id, long expectedVersion, NewItem data) {
        var current = currentVersion(id, expectedVersion);
        requireCategory(data);
        return items.update(current.replace(data));
    }

    @Override
    public Item patch(ItemId id, long expectedVersion, ItemPatch patch) {
        var current = currentVersion(id, expectedVersion);
        var price = Money.of(
                apply(patch.priceAmount(), current.price().amount().toPlainString()),
                apply(patch.priceCurrency(), current.price().currency()));
        var data = new NewItem(
                apply(patch.name(), current.name()),
                apply(patch.description(), current.description()),
                price,
                apply(patch.categoryId(), current.categoryId()));
        requireCategory(data);
        return items.update(current.replace(data));
    }

    @Override
    public void delete(ItemId id, long expectedVersion) {
        currentVersion(id, expectedVersion);
        items.delete(id);
    }

    /** Loads the item and checks the client's If-Match version against it. */
    private Item currentVersion(ItemId id, long expectedVersion) {
        var current = get(id);
        if (current.version() != expectedVersion) {
            throw new VersionConflictException("Item " + id.value() + " is at version " + current.version()
                    + ", but If-Match was " + expectedVersion);
        }
        return current;
    }

    private void requireCategory(NewItem data) {
        if (categories.findById(data.categoryId()).isEmpty()) {
            throw new UnknownCategoryException("Category " + data.categoryId().value() + " does not exist");
        }
    }

    private static <T> T apply(PatchValue<T> value, T current) {
        return switch (value) {
            case PatchValue.Absent<T> absent -> current;
            case PatchValue.Remove<T> remove -> null;
            case PatchValue.Set<T> set -> set.value();
        };
    }

    @Override
    @Transactional(readOnly = true)
    public Item get(ItemId id) {
        return items.findById(id).orElseThrow(() -> new ItemNotFoundException("Item " + id.value() + " not found"));
    }
}
