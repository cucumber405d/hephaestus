package de.ibikus.appa.hephaestus.restservice.catalog.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.ibikus.appa.hephaestus.restservice.catalog.PostgresTestcontainers;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.Category;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.CategoryId;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.Item;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.Money;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.NewItem;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.UnknownCategoryException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestcontainers.class, JpaCategoryRepository.class, JpaItemRepository.class, PersistenceMapperImpl.class
})
class JpaItemRepositoryIT {

    private static final Instant CREATED = Instant.parse("2026-11-09T10:00:00.123456Z");

    @Autowired
    JpaCategoryRepository categories;

    @Autowired
    JpaItemRepository items;

    @Test
    void insertAndFindRoundTrip() {
        var swords = categories.insert(new Category(CategoryId.newId(), "Swords", 0));
        var item = Item.create(new NewItem("Bronze sword", "Sharp", Money.of("120.5", "EUR"), swords.id()), CREATED);

        var saved = items.insert(item);

        assertThat(saved.version()).isZero();
        assertThat(items.findById(item.id())).contains(saved);
        assertThat(saved.price()).isEqualTo(Money.of("120.50", "EUR"));
        assertThat(saved.createdAt()).isEqualTo(CREATED);
        assertThat(items.existsByCategory(swords.id())).isTrue();
    }

    @Test
    void databaseRejectsUnknownCategory() {
        var item = Item.create(new NewItem("Orphan", null, Money.of("1", "EUR"), CategoryId.newId()), CREATED);

        assertThatThrownBy(() -> items.insert(item)).isInstanceOf(UnknownCategoryException.class);
    }
}
