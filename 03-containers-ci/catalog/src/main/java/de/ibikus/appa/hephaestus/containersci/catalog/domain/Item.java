package de.ibikus.appa.hephaestus.containersci.catalog.domain;

import java.time.Instant;
import java.util.Objects;

/** A catalog item. Items are created and read; this service does not change them. */
public record Item(ItemId id, String name, String description, Money price, CategoryId categoryId, Instant createdAt) {

    public Item {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(createdAt, "createdAt");
        var data = new NewItem(name, description, price, categoryId);
        name = data.name();
        description = data.description();
    }

    public static Item create(NewItem data, Instant now) {
        return new Item(ItemId.newId(), data.name(), data.description(), data.price(), data.categoryId(), now);
    }
}
