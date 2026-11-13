package de.ibikus.appa.hephaestus.containersci.catalog.adapter.out.persistence;

import de.ibikus.appa.hephaestus.containersci.catalog.application.port.out.ItemRepository;
import de.ibikus.appa.hephaestus.containersci.catalog.domain.CategoryId;
import de.ibikus.appa.hephaestus.containersci.catalog.domain.Item;
import de.ibikus.appa.hephaestus.containersci.catalog.domain.ItemId;
import de.ibikus.appa.hephaestus.containersci.catalog.domain.UnknownCategoryException;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;

/** Item persistence; a violation of the category foreign key is translated into UnknownCategoryException. */
@Repository
class JpaItemRepository implements ItemRepository {

    private final ItemSpringDataRepository repository;

    JpaItemRepository(ItemSpringDataRepository repository) {
        this.repository = repository;
    }

    @Override
    public Item insert(Item item) {
        try {
            return PersistenceMapper.toDomain(repository.saveAndFlush(PersistenceMapper.toNewEntity(item)));
        } catch (DataIntegrityViolationException e) {
            // Only the category foreign key is the client's mistake; any other violation is a bug and stays a 500
            if (Constraints.violated(e, "item_category_fk")) {
                throw new UnknownCategoryException(
                        "Category " + item.categoryId().value() + " does not exist");
            }
            throw e;
        }
    }

    @Override
    public Optional<Item> findById(ItemId id) {
        return repository.findById(id.value()).map(PersistenceMapper::toDomain);
    }

    @Override
    public List<Item> findAll(Optional<CategoryId> category, int limit) {
        var entities = category.map(id -> repository.findByCategoryIdOrderByNameAsc(id.value(), Limit.of(limit)))
                .orElseGet(() -> repository.findAllByOrderByNameAsc(Limit.of(limit)));
        return entities.stream().map(PersistenceMapper::toDomain).toList();
    }
}
