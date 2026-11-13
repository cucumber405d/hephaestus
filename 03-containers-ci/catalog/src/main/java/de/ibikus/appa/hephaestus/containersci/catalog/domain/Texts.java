package de.ibikus.appa.hephaestus.containersci.catalog.domain;

/** Validation helpers for text fields shared by the domain types. */
final class Texts {

    private Texts() {}

    /** Strips the value and requires 1..maxLength characters. */
    static String required(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new InvalidValueException(field + " must not be blank");
        }
        var stripped = value.strip();
        if (stripped.length() > maxLength) {
            throw new InvalidValueException(field + " must be at most " + maxLength + " characters");
        }
        return stripped;
    }

    /** Null stays null; otherwise at most maxLength characters. */
    static String optional(String value, String field, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new InvalidValueException(field + " must be at most " + maxLength + " characters");
        }
        return value;
    }
}
