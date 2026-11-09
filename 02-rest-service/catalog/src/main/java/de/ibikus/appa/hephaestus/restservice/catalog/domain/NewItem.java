package de.ibikus.appa.hephaestus.restservice.catalog.domain;

/** The client-controlled fields of an item, validated; used for create and full replace. */
public record NewItem(String name, String description, Money price, CategoryId categoryId) {

    public static final int MAX_NAME_LENGTH = 200;
    public static final int MAX_DESCRIPTION_LENGTH = 2000;

    public NewItem {
        name = Texts.required(name, "name", MAX_NAME_LENGTH);
        description = Texts.optional(description, "description", MAX_DESCRIPTION_LENGTH);
        if (price == null) {
            throw new InvalidValueException("price is required");
        }
        if (categoryId == null) {
            throw new InvalidValueException("categoryId is required");
        }
    }
}
