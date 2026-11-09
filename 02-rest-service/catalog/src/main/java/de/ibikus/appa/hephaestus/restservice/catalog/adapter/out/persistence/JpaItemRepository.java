package de.ibikus.appa.hephaestus.restservice.catalog.adapter.out.persistence;

import de.ibikus.appa.hephaestus.restservice.catalog.application.port.out.ItemRepository;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.CategoryId;
import org.springframework.stereotype.Repository;

/** Item persistence. */
@Repository
class JpaItemRepository implements ItemRepository {

    private final ItemSpringDataRepository repository;

    JpaItemRepository(ItemSpringDataRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean existsByCategory(CategoryId categoryId) {
        return repository.existsByCategoryId(categoryId.value());
    }
}
