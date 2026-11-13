package de.ibikus.appa.hephaestus.containersci.catalog.application.port.out;

import de.ibikus.appa.hephaestus.containersci.catalog.domain.CategoryId;
import de.ibikus.appa.hephaestus.containersci.catalog.domain.Item;
import de.ibikus.appa.hephaestus.containersci.catalog.domain.ItemId;
import java.util.List;
import java.util.Optional;

/** Outbound port: item storage. */
public interface ItemRepository {

    /** Stores a new item; throws UnknownCategoryException if its category does not exist. */
    Item insert(Item item);

    Optional<Item> findById(ItemId id);

    /** Items ordered by name, optionally of one category, at most {@code limit}. */
    List<Item> findAll(Optional<CategoryId> category, int limit);
}
