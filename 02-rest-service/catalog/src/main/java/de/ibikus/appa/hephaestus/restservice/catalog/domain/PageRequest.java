package de.ibikus.appa.hephaestus.restservice.catalog.domain;

/** Offset pagination: zero-based page and a size of 1..100. */
public record PageRequest(int page, int size) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public PageRequest {
        if (page < 0) {
            throw new InvalidValueException("page must not be negative");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new InvalidValueException("size must be between 1 and " + MAX_SIZE);
        }
    }
}
