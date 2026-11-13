package de.ibikus.appa.hephaestus.containersci.catalog.adapter.out.persistence;

import de.ibikus.appa.hephaestus.containersci.catalog.domain.Category;
import de.ibikus.appa.hephaestus.containersci.catalog.domain.CategoryId;
import de.ibikus.appa.hephaestus.containersci.catalog.domain.Item;
import de.ibikus.appa.hephaestus.containersci.catalog.domain.ItemId;
import de.ibikus.appa.hephaestus.containersci.catalog.domain.Money;

/** Domain <-> JPA entity mapping, written by hand: two small types do not justify MapStruct here. */
final class PersistenceMapper {

    private PersistenceMapper() {}

    static ItemJpaEntity toNewEntity(Item item) {
        var entity = new ItemJpaEntity();
        entity.setId(item.id().value());
        entity.setName(item.name());
        entity.setDescription(item.description());
        entity.setPriceAmount(item.price().amount());
        entity.setPriceCurrency(item.price().currency());
        entity.setCategoryId(item.categoryId().value());
        entity.setCreatedAt(item.createdAt());
        return entity;
    }

    static Item toDomain(ItemJpaEntity entity) {
        return new Item(
                new ItemId(entity.getId()),
                entity.getName(),
                entity.getDescription(),
                new Money(entity.getPriceAmount(), entity.getPriceCurrency()),
                new CategoryId(entity.getCategoryId()),
                entity.getCreatedAt());
    }

    static Category toDomain(CategoryJpaEntity entity) {
        return new Category(new CategoryId(entity.getId()), entity.getName());
    }
}
