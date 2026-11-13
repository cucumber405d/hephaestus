package de.ibikus.appa.hephaestus.containersci.catalog.application.port.out;

import de.ibikus.appa.hephaestus.containersci.catalog.domain.Category;
import de.ibikus.appa.hephaestus.containersci.catalog.domain.CategoryId;
import java.util.List;

/** Outbound port: category storage (read-only). */
public interface CategoryRepository {

    boolean exists(CategoryId id);

    List<Category> findAllOrderedByName();
}
