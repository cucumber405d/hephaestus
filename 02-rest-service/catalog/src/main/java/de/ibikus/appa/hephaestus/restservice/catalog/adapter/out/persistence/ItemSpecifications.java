package de.ibikus.appa.hephaestus.restservice.catalog.adapter.out.persistence;

import de.ibikus.appa.hephaestus.restservice.catalog.domain.ItemSearchCriteria;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.SortOrder;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

/** Translates search criteria into a JPA Criteria query and a whitelisted sort. */
final class ItemSpecifications {

    private static final char ESCAPE = '\\';

    private ItemSpecifications() {}

    static Specification<ItemJpaEntity> matching(ItemSearchCriteria criteria) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (criteria.category() != null) {
                predicates.add(
                        cb.equal(root.get("categoryId"), criteria.category().value()));
            }
            if (criteria.minPrice() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("priceAmount"), criteria.minPrice()));
            }
            if (criteria.maxPrice() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("priceAmount"), criteria.maxPrice()));
            }
            if (criteria.nameContains() != null && !criteria.nameContains().isBlank()) {
                var pattern = "%" + escapeLike(criteria.nameContains().strip().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.like(cb.lower(root.get("name")), pattern, ESCAPE));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** Sort orders map to entity attributes from a fixed list; the id makes paging stable for equal values. */
    static Sort sort(List<SortOrder> orders) {
        var sort = Sort.unsorted();
        for (var order : orders) {
            var property = switch (order.field()) {
                case NAME -> "name";
                case PRICE -> "priceAmount";
                case CREATED_AT -> "createdAt";
            };
            sort = sort.and(
                    order.descending()
                            ? Sort.by(property).descending()
                            : Sort.by(property).ascending());
        }
        return sort.and(Sort.by("id"));
    }

    /** User input must not act as a wildcard: escape the escape character first, then % and _. */
    static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
