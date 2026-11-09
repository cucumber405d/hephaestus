package de.ibikus.appa.hephaestus.restservice.catalog.domain;

import java.util.List;

/** One page of a result. */
public record Page<T>(List<T> content, int page, int size, long totalElements) {

    public Page {
        content = List.copyOf(content);
    }

    public int totalPages() {
        return size == 0 ? 0 : (int) ((totalElements + size - 1) / size);
    }
}
