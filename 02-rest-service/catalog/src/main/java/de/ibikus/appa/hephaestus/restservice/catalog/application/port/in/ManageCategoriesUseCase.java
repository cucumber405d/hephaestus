package de.ibikus.appa.hephaestus.restservice.catalog.application.port.in;

import de.ibikus.appa.hephaestus.restservice.catalog.domain.Category;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.CategoryId;
import java.util.List;

/** Driving port for category management. */
public interface ManageCategoriesUseCase {

    Category create(String name);

    List<Category> list();

    Category get(CategoryId id);

    void delete(CategoryId id);
}
