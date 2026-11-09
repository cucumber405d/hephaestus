package de.ibikus.appa.hephaestus.restservice.catalog.domain;

import java.util.Objects;
import java.util.UUID;

/** Identity of a category; items reference categories only by this id (aggregates reference each other by id). */
public record CategoryId(UUID value) {

    public CategoryId {
        Objects.requireNonNull(value, "value");
    }

    public static CategoryId newId() {
        return new CategoryId(Ids.uuidV7());
    }
}
