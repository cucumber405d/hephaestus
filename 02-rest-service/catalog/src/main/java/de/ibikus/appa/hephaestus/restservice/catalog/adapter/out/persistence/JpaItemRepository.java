package de.ibikus.appa.hephaestus.restservice.catalog.adapter.out.persistence;

import de.ibikus.appa.hephaestus.restservice.catalog.application.port.out.ItemRepository;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.CategoryId;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.Item;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.ItemId;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.ItemSearchCriteria;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.Page;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.PageRequest;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.UnknownCategoryException;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

/** Item persistence; the foreign key to category is translated into UnknownCategoryException. */
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
            throw new UnknownCategoryException("Category " + item.categoryId().value() + " does not exist");
        }
    }

    @Override
    public Optional<Item> findById(ItemId id) {
        return repository.findById(id.value()).map(mapper::toDomain);
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
