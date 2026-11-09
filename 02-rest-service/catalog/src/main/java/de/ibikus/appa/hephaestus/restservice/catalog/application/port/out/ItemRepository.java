package de.ibikus.appa.hephaestus.restservice.catalog.application.port.out;

import de.ibikus.appa.hephaestus.restservice.catalog.domain.CategoryId;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.Item;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.ItemId;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.ItemSearchCriteria;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.Page;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.PageRequest;
import java.util.Optional;

/** Driven port: item persistence. */
public interface ItemRepository {

    /** Stores a new item (version 0); a missing category raises UnknownCategoryException. */
    Item insert(Item item);

    Optional<Item> findById(ItemId id);

    /** Writes changed fields of an existing item; the version increments. A missing category raises UnknownCategory. */
    Item update(Item item);

    void delete(ItemId id);

    boolean existsByCategory(CategoryId categoryId);

    Page<Item> search(ItemSearchCriteria criteria, PageRequest pageRequest);
}
