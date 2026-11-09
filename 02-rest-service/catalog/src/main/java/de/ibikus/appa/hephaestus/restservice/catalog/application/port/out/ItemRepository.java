package de.ibikus.appa.hephaestus.restservice.catalog.application.port.out;

import de.ibikus.appa.hephaestus.restservice.catalog.domain.CategoryId;

/** Driven port: item persistence. */
public interface ItemRepository {

    boolean existsByCategory(CategoryId categoryId);
}
