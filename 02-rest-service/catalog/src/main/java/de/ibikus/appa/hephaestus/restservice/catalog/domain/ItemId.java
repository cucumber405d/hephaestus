package de.ibikus.appa.hephaestus.restservice.catalog.domain;

import java.util.Objects;
import java.util.UUID;

/** Identity of an item. */
public record ItemId(UUID value) {

    public ItemId {
        Objects.requireNonNull(value, "value");
    }

    public static ItemId newId() {
        return new ItemId(Ids.uuidV7());
    }
}
