package de.ibikus.appa.hephaestus.restservice.catalog.application.port.out;

import de.ibikus.appa.hephaestus.restservice.catalog.domain.CategoryId;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.Item;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.ItemId;
import java.util.Optional;

/** Driven port: item persistence. */
public interface ItemRepository {

    /** Stores a new item (version 0); a missing category raises UnknownCategoryException. */
    Item insert(Item item);

    Optional<Item> findById(ItemId id);

    boolean existsByCategory(CategoryId categoryId);
}
