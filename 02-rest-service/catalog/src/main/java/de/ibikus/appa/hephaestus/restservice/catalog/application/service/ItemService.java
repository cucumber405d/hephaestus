package de.ibikus.appa.hephaestus.restservice.catalog.application.service;

import de.ibikus.appa.hephaestus.restservice.catalog.application.port.in.CreationResult;
import de.ibikus.appa.hephaestus.restservice.catalog.application.port.in.IdempotencyKey;
import de.ibikus.appa.hephaestus.restservice.catalog.application.port.in.ItemPatch;
import de.ibikus.appa.hephaestus.restservice.catalog.application.port.in.ManageItemsUseCase;
import de.ibikus.appa.hephaestus.restservice.catalog.application.port.in.PatchValue;
import de.ibikus.appa.hephaestus.restservice.catalog.application.port.in.SearchItemsQuery;
import de.ibikus.appa.hephaestus.restservice.catalog.application.port.out.CategoryRepository;
import de.ibikus.appa.hephaestus.restservice.catalog.application.port.out.IdempotencyRecord;
import de.ibikus.appa.hephaestus.restservice.catalog.application.port.out.IdempotencyStore;
import de.ibikus.appa.hephaestus.restservice.catalog.application.port.out.ItemRepository;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.IdempotencyConflictException;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Item use cases. */
@Service
@Transactional
public class ItemService implements ManageItemsUseCase, SearchItemsQuery {

    private final ItemRepository items;
    private final CategoryRepository categories;
    private final IdempotencyStore idempotency;
    private final Clock clock;

    public ItemService(ItemRepository items, CategoryRepository categories, IdempotencyStore idempotency, Clock clock) {
        this.items = items;
        this.categories = categories;
        this.idempotency = idempotency;
        this.clock = clock;
    }

    /**
     * Runs in one transaction with the item insert: either the key and the item are both committed or neither is.
     * The key is inserted before the item, so a concurrent request with the same key blocks on the primary key until
     * this transaction ends and then fails with IdempotencyConflictException instead of creating a second item.
     */
    @Override
    public CreationResult create(NewItem data, IdempotencyKey key) {
        var hash = requestHash(data);
        var existing = idempotency.find(key.value());
        if (existing.isPresent()) {
            var record = existing.get();
            if (!record.requestHash().equals(hash)) {
                throw new IdempotencyConflictException("This Idempotency-Key was already used for a different request");
            }
            var item = items.findById(record.itemId())
                    .orElseThrow(() -> new IdempotencyConflictException(
                            "The item created with this Idempotency-Key no longer exists"));
            return new CreationResult(item, true);
        }
        requireCategory(data);
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        var item = Item.create(data, now);
        idempotency.insert(new IdempotencyRecord(key.value(), hash, item.id(), now));
        return new CreationResult(items.insert(item), false);
    }

    /**
     * SHA-256 over the normalized domain data, not the raw body: the same item sent with another field order,
     * whitespace or "120" vs "120.00" is the same request. Each field is length-prefixed so that values containing
     * the separator cannot collide.
     */
    static String requestHash(NewItem data) {
        var canonical = new StringBuilder();
        for (var field : new String[] {
            data.name(),
            data.description(),
            data.price().amount().toPlainString(),
            data.price().currency(),
            data.categoryId().value().toString()
        }) {
            canonical.append(field == null ? "-1" : field.length()).append(':').append(Objects.toString(field, ""));
            canonical.append('|');
        }
        try {
            var digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java platform", e);
        }
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
