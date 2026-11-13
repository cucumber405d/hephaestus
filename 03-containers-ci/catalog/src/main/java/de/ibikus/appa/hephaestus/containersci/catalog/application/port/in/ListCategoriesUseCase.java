package de.ibikus.appa.hephaestus.containersci.catalog.application.port.in;

import de.ibikus.appa.hephaestus.containersci.catalog.domain.Category;
import java.util.List;

/** Inbound port for categories (read-only). */
public interface ListCategoriesUseCase {

    /** All categories ordered by name. */
    List<Category> list();
}
