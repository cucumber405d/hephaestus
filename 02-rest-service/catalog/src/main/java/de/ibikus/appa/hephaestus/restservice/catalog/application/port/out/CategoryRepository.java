package de.ibikus.appa.hephaestus.restservice.catalog.application.port.out;

import de.ibikus.appa.hephaestus.restservice.catalog.domain.Category;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.CategoryId;
import java.util.List;
import java.util.Optional;

/** Driven port: category persistence. Implementations translate constraint violations into domain exceptions. */
public interface CategoryRepository {

    /** Stores a new category; a duplicate name ignoring case raises DuplicateCategoryNameException. */
    Category insert(Category category);

    Optional<Category> findById(CategoryId id);

    List<Category> findAllOrderedByName();

    boolean existsByNameIgnoreCase(String name);

    /** Deletes the category; if items still reference it, raises CategoryInUseException. */
    void delete(CategoryId id);
}
