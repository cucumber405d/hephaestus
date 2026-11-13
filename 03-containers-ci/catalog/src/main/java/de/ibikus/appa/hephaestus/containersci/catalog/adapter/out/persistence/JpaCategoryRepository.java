package de.ibikus.appa.hephaestus.containersci.catalog.adapter.out.persistence;

import de.ibikus.appa.hephaestus.containersci.catalog.application.port.out.CategoryRepository;
import de.ibikus.appa.hephaestus.containersci.catalog.domain.Category;
import de.ibikus.appa.hephaestus.containersci.catalog.domain.CategoryId;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
class JpaCategoryRepository implements CategoryRepository {

    private final CategorySpringDataRepository repository;

    JpaCategoryRepository(CategorySpringDataRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean exists(CategoryId id) {
        return repository.existsById(id.value());
    }

    @Override
    public List<Category> findAllOrderedByName() {
        return repository.findAllByOrderByNameAsc().stream()
                .map(PersistenceMapper::toDomain)
                .toList();
    }
}
