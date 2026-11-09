package de.ibikus.appa.hephaestus.restservice.catalog.adapter.out.persistence;

import de.ibikus.appa.hephaestus.restservice.catalog.application.port.out.ItemRepository;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.CategoryId;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.Item;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.ItemId;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.ItemNotFoundException;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.ItemSearchCriteria;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.Page;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.PageRequest;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.UnknownCategoryException;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

/** Item persistence; a violation of the category foreign key is translated into UnknownCategoryException. */
@Repository
class JpaItemRepository implements ItemRepository {

    private final ItemSpringDataRepository repository;
    private final PersistenceMapper mapper;

    JpaItemRepository(ItemSpringDataRepository repository, PersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Item insert(Item item) {
        try {
            return mapper.toDomain(repository.saveAndFlush(mapper.toNewEntity(item)));
        } catch (DataIntegrityViolationException e) {
            throw translate(e, item);
        }
    }

    @Override
    public Optional<Item> findById(ItemId id) {
        return repository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public Item update(Item item) {
        var entity = repository
                .findById(item.id().value())
                .orElseThrow(() -> new ItemNotFoundException("Item " + item.id().value() + " not found"));
        entity.setName(item.name());
        entity.setDescription(item.description());
        entity.setPriceAmount(item.price().amount());
        entity.setPriceCurrency(item.price().currency());
        entity.setCategoryId(item.categoryId().value());
        try {
            // UPDATE ... WHERE id = ? AND version = ?: a concurrent commit makes it hit 0 rows -> optimistic lock
            // failure
            repository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException e) {
            throw translate(e, item);
        }
        return mapper.toDomain(entity);
    }

    /** Only the category foreign key is the client's mistake; any other violation is a bug and stays a 500. */
    private static RuntimeException translate(DataIntegrityViolationException e, Item item) {
        if (Constraints.violated(e, "item_category_fk")) {
            return new UnknownCategoryException("Category " + item.categoryId().value() + " does not exist");
        }
        return e;
    }

    @Override
    public void delete(ItemId id) {
        repository.deleteById(id.value());
        repository.flush();
    }

    @Override
    public Page<Item> search(ItemSearchCriteria criteria, PageRequest pageRequest) {
        var pageable = org.springframework.data.domain.PageRequest.of(
                pageRequest.page(), pageRequest.size(), ItemSpecifications.sort(criteria.sort()));
        var result = repository.findAll(ItemSpecifications.matching(criteria), pageable);
        return new Page<>(
                result.getContent().stream().map(mapper::toDomain).toList(),
                pageRequest.page(),
                pageRequest.size(),
                result.getTotalElements());
    }

    @Override
    public boolean existsByCategory(CategoryId categoryId) {
        return repository.existsByCategoryId(categoryId.value());
    }
}
