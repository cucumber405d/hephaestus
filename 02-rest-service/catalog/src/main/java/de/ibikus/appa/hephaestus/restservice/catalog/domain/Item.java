package de.ibikus.appa.hephaestus.restservice.catalog.domain;

import java.time.Instant;
import java.util.Objects;

/** A catalog item. The version is the optimistic-locking counter that the web adapter exposes as ETag. */
public record Item(
        ItemId id,
        String name,
        String description,
        Money price,
        CategoryId categoryId,
        Instant createdAt,
        long version) {

    public Item {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(createdAt, "createdAt");
        var data = new NewItem(name, description, price, categoryId);
        name = data.name();
        description = data.description();
    }

    public static Item create(NewItem data, Instant now) {
        return new Item(ItemId.newId(), data.name(), data.description(), data.price(), data.categoryId(), now, 0);
    }

    /** Full replacement of the client-controlled fields; identity, creation time and version stay. */
    public Item replace(NewItem data) {
        return new Item(id, data.name(), data.description(), data.price(), data.categoryId(), createdAt, version);
    }
}
