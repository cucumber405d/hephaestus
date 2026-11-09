package de.ibikus.appa.hephaestus.restservice.catalog.domain;

import java.math.BigDecimal;
import java.util.List;

/** Filters and sort order of an item search; all filters are optional (null). Sorted by name when no order is given. */
public record ItemSearchCriteria(
        CategoryId category, BigDecimal minPrice, BigDecimal maxPrice, String nameContains, List<SortOrder> sort) {

    public ItemSearchCriteria {
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new InvalidValueException("minPrice must not be greater than maxPrice");
        }
        sort = sort == null || sort.isEmpty() ? List.of(new SortOrder(SortField.NAME, false)) : List.copyOf(sort);
    }
}
