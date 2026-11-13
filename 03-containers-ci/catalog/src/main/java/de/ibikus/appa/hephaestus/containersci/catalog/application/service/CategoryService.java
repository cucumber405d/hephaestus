package de.ibikus.appa.hephaestus.containersci.catalog.application.service;

import de.ibikus.appa.hephaestus.containersci.catalog.application.port.in.ListCategoriesUseCase;
import de.ibikus.appa.hephaestus.containersci.catalog.application.port.out.CategoryRepository;
import de.ibikus.appa.hephaestus.containersci.catalog.domain.Category;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService implements ListCategoriesUseCase {

    private final CategoryRepository categories;

    public CategoryService(CategoryRepository categories) {
        this.categories = categories;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Category> list() {
        return categories.findAllOrderedByName();
    }
}
