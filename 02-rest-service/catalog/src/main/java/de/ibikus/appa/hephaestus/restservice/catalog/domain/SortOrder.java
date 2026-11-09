package de.ibikus.appa.hephaestus.restservice.catalog.domain;

/** One sort criterion; parsed from the API form {@code name}, {@code -price}, {@code createdAt}. */
public record SortOrder(SortField field, boolean descending) {

    public static SortOrder parse(String text) {
        if (text == null || text.isBlank()) {
            throw new InvalidValueException("Sort field must not be blank");
        }
        boolean descending = text.startsWith("-");
        var name = descending ? text.substring(1) : text;
        var field = switch (name) {
            case "name" -> SortField.NAME;
            case "price" -> SortField.PRICE;
            case "createdAt" -> SortField.CREATED_AT;
            default ->
                throw new InvalidValueException("Unknown sort field '" + name + "'; allowed: name, price, createdAt");
        };
        return new SortOrder(field, descending);
    }
}
