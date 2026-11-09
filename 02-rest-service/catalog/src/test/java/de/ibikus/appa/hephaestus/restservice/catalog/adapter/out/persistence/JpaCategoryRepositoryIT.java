package de.ibikus.appa.hephaestus.restservice.catalog.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.ibikus.appa.hephaestus.restservice.catalog.PostgresTestcontainers;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.Category;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.CategoryId;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.CategoryInUseException;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.DuplicateCategoryNameException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestcontainers.class, JpaCategoryRepository.class, JpaItemRepository.class, PersistenceMapperImpl.class
})
class JpaCategoryRepositoryIT {

    @Autowired
    JpaCategoryRepository categories;

    @Autowired
    JpaItemRepository items;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void insertStartsAtVersionZero() {
        var saved = categories.insert(new Category(CategoryId.newId(), "Swords", 0));

        assertThat(saved.version()).isZero();
        assertThat(categories.findById(saved.id())).contains(saved);
    }

    @Test
    void listsByNameAndFindsNamesIgnoringCase() {
        categories.insert(new Category(CategoryId.newId(), "Shields", 0));
        categories.insert(new Category(CategoryId.newId(), "Axes", 0));

        assertThat(categories.findAllOrderedByName()).extracting(Category::name).containsExactly("Axes", "Shields");
        assertThat(categories.existsByNameIgnoreCase("AXES")).isTrue();
        assertThat(categories.existsByNameIgnoreCase("Hammers")).isFalse();
    }

    @Test
    void databaseRejectsDuplicateNameEvenWithoutTheServiceCheck() {
        categories.insert(new Category(CategoryId.newId(), "Swords", 0));

        assertThatThrownBy(() -> categories.insert(new Category(CategoryId.newId(), "SWORDS", 0)))
                .isInstanceOf(DuplicateCategoryNameException.class);
    }

    @Test
    void databaseRejectsDeletingACategoryThatStillHasItems() {
        var swords = categories.insert(new Category(CategoryId.newId(), "Swords", 0));
        jdbc.update(
                "insert into item (id, name, price_amount, price_currency, category_id, created_at, version)"
                        + " values (gen_random_uuid(), 'Bronze sword', 120, 'EUR', ?, now(), 0)",
                swords.id().value());

        assertThat(items.existsByCategory(swords.id())).isTrue();
        assertThatThrownBy(() -> categories.delete(swords.id())).isInstanceOf(CategoryInUseException.class);
    }

    @Test
    void deletesUnusedCategory() {
        var swords = categories.insert(new Category(CategoryId.newId(), "Swords", 0));

        categories.delete(swords.id());

        assertThat(categories.findById(swords.id())).isEmpty();
    }
}
