package de.ibikus.appa.hephaestus.restservice.catalog.application.port.in;

import de.ibikus.appa.hephaestus.restservice.catalog.domain.Item;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.ItemId;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.NewItem;

/** Driving port for item management. */
public interface ManageItemsUseCase {

    /** Creates an item; a category that does not exist raises UnknownCategoryException. */
    Item create(NewItem data);

    /**
     * Creates an item at most once per Idempotency-Key: a retry with the same key and the same data returns the item
     * created first; the same key with different data raises IdempotencyConflictException.
     */
    CreationResult create(NewItem data, IdempotencyKey key);

    Item get(ItemId id);

    /** Replaces all client fields; {@code expectedVersion} differing from the current one raises VersionConflict. */
    Item replace(ItemId id, long expectedVersion, NewItem data);

    /** Applies a merge patch; same version rule as replace. */
    Item patch(ItemId id, long expectedVersion, ItemPatch patch);

    void delete(ItemId id, long expectedVersion);
}
