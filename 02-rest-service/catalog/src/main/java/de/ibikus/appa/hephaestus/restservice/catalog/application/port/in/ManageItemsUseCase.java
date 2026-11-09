package de.ibikus.appa.hephaestus.restservice.catalog.application.port.in;

import de.ibikus.appa.hephaestus.restservice.catalog.domain.Item;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.ItemId;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.NewItem;

/** Driving port for item management. */
public interface ManageItemsUseCase {

    /** Creates an item; a category that does not exist raises UnknownCategoryException. */
    Item create(NewItem data);

    Item get(ItemId id);
}
