package de.ibikus.appa.hephaestus.restservice.catalog.adapter.out.persistence;

import de.ibikus.appa.hephaestus.restservice.catalog.application.port.out.CategoryRepository;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.Category;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.CategoryId;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.CategoryInUseException;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.DuplicateCategoryNameException;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

/**
 * Category persistence. Writes are flushed here so that constraint violations surface inside the adapter and can be
 * translated into domain exceptions (unique name, foreign key from items).
 */
@Repository
class JpaCategoryRepository implements CategoryRepository {

    private final CategorySpringDataRepository repository;
    private final PersistenceMapper mapper;

    JpaCategoryRepository(CategorySpringDataRepository repository, PersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Category insert(Category category) {
        try {
            return mapper.toDomain(repository.saveAndFlush(mapper.toNewEntity(category)));
        } catch (DataIntegrityViolationException e) {
            if (Constraints.violated(e, "category_name_ci")) {
                throw new DuplicateCategoryNameException("Category '" + category.name() + "' already exists");
            }
            throw e;
        }
    }

    @Override
    public Optional<Category> findById(CategoryId id) {
        return repository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public List<Category> findAllOrderedByName() {
        return repository.findAllByOrderByNameAsc().stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public boolean existsByNameIgnoreCase(String name) {
        return repository.existsByNameIgnoreCase(name);
    }

    @Override
    public void delete(CategoryId id) {
        try {
            repository.deleteById(id.value());
            repository.flush();
        } catch (DataIntegrityViolationException e) {
            if (Constraints.violated(e, "item_category_fk")) {
                throw new CategoryInUseException("Category " + id.value() + " still has items");
            }
            throw e;
        }
    }
}
