package de.ibikus.appa.hephaestus.containersci.catalog.adapter.in.web;

import de.ibikus.appa.hephaestus.containersci.catalog.domain.Category;
import de.ibikus.appa.hephaestus.containersci.catalog.domain.CategoryId;
import de.ibikus.appa.hephaestus.containersci.catalog.domain.InvalidValueException;
import de.ibikus.appa.hephaestus.containersci.catalog.domain.Item;
import de.ibikus.appa.hephaestus.containersci.catalog.domain.Money;
import de.ibikus.appa.hephaestus.containersci.catalog.domain.NewItem;

/** JSON records <-> domain, by hand. */
final class WebMapper {

    private WebMapper() {}

    static NewItem toNewItem(ItemRequest request) {
        if (request.price() == null) {
            throw new InvalidValueException("price is required");
        }
        var category = request.categoryId() == null ? null : new CategoryId(request.categoryId());
        return new NewItem(
                request.name(),
                request.description(),
                Money.of(request.price().amount(), request.price().currency()),
                category);
    }

    static ItemResponse toResponse(Item item) {
        return new ItemResponse(
                item.id().value(),
                item.name(),
                item.description(),
                new PriceJson(
                        item.price().amount().toPlainString(), item.price().currency()),
                item.categoryId().value(),
                item.createdAt());
    }

    static CategoryResponse toResponse(Category category) {
        return new CategoryResponse(category.id().value(), category.name());
    }
}
