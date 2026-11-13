package de.ibikus.appa.hephaestus.containersci.catalog.application.port.in;

import de.ibikus.appa.hephaestus.containersci.catalog.domain.CategoryId;
import de.ibikus.appa.hephaestus.containersci.catalog.domain.Item;
import de.ibikus.appa.hephaestus.containersci.catalog.domain.ItemId;
import de.ibikus.appa.hephaestus.containersci.catalog.domain.NewItem;
import java.util.List;
import java.util.Optional;

/** Inbound port for items. */
public interface ManageItemsUseCase {

    Item create(NewItem data);

    Item get(ItemId id);

    /** Items ordered by name, optionally of one category; at most {@code ItemService.LIST_LIMIT}. */
    List<Item> list(Optional<CategoryId> category);
}
