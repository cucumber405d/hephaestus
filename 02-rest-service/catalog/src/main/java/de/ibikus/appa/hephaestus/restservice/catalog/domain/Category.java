package de.ibikus.appa.hephaestus.restservice.catalog.domain;

/** A product category. The name is stripped; uniqueness ignoring case is enforced by the application and the DB. */
public record Category(CategoryId id, String name, long version) {

    public static final int MAX_NAME_LENGTH = 100;

    public Category {
        if (id == null) {
            throw new InvalidValueException("Category id is required");
        }
        name = Texts.required(name, "name", MAX_NAME_LENGTH);
    }
}
