package de.ibikus.appa.hephaestus.restservice.catalog.application.service;

import de.ibikus.appa.hephaestus.restservice.catalog.application.port.in.ManageCategoriesUseCase;
import de.ibikus.appa.hephaestus.restservice.catalog.application.port.out.CategoryRepository;
import de.ibikus.appa.hephaestus.restservice.catalog.application.port.out.ItemRepository;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.Category;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.CategoryId;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.CategoryInUseException;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.CategoryNotFoundException;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.DuplicateCategoryNameException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Category use cases. Uniqueness and "no items left" are checked here for a clear error; the database constraints
 * stay the last line of defence against concurrent requests.
 */
@Service
@Transactional
public class CategoryService implements ManageCategoriesUseCase {

    private final CategoryRepository categories;
    private final ItemRepository items;

    public CategoryService(CategoryRepository categories, ItemRepository items) {
        this.categories = categories;
        this.items = items;
    }

    @Override
    public Category create(String name) {
        var category = new Category(CategoryId.newId(), name, 0);
        if (categories.existsByNameIgnoreCase(category.name())) {
            throw new DuplicateCategoryNameException("Category '" + category.name() + "' already exists");
        }
        return categories.insert(category);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Category> list() {
        return categories.findAllOrderedByName();
    }

    @Override
    @Transactional(readOnly = true)
    public Category get(CategoryId id) {
        return categories
                .findById(id)
                .orElseThrow(() -> new CategoryNotFoundException("Category " + id.value() + " not found"));
    }

    @Override
    public void delete(CategoryId id) {
        get(id);
        if (items.existsByCategory(id)) {
            throw new CategoryInUseException("Category " + id.value() + " still has items");
        }
        categories.delete(id);
    }
}
