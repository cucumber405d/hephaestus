package de.ibikus.appa.hephaestus.containersci.catalog.domain;

/** A product category; read-only in this service (seeded by a migration). */
public record Category(CategoryId id, String name) {

    public static final int MAX_NAME_LENGTH = 100;

    public Category {
        if (id == null) {
            throw new InvalidValueException("Category id is required");
        }
        name = Texts.required(name, "name", MAX_NAME_LENGTH);
    }
}
