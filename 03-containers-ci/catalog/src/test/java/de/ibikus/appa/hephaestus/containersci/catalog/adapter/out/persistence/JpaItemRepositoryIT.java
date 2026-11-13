package de.ibikus.appa.hephaestus.containersci.catalog.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.ibikus.appa.hephaestus.containersci.catalog.PostgresTestcontainers;
import de.ibikus.appa.hephaestus.containersci.catalog.domain.CategoryId;
import de.ibikus.appa.hephaestus.containersci.catalog.domain.Item;
import de.ibikus.appa.hephaestus.containersci.catalog.domain.Money;
import de.ibikus.appa.hephaestus.containersci.catalog.domain.NewItem;
import de.ibikus.appa.hephaestus.containersci.catalog.domain.UnknownCategoryException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestcontainers.class, JpaItemRepository.class})
class JpaItemRepositoryIT {

    static final CategoryId ARMOR = new CategoryId(UUID.fromString("01900000-0000-7000-8000-000000000001"));
    static final CategoryId WEAPONS = new CategoryId(UUID.fromString("01900000-0000-7000-8000-000000000003"));
    private static final Instant CREATED = Instant.parse("2026-11-13T10:00:00.123456Z");

    @Autowired
    JpaItemRepository items;

    @Test
    void insertAndFindRoundTrip() {
        var item = Item.create(new NewItem("Bronze sword", "Sharp", Money.of("12.5", "EUR"), WEAPONS), CREATED);

        items.insert(item);

        var found = items.findById(item.id()).orElseThrow();
        assertThat(found).isEqualTo(item);
        assertThat(found.price().amount().toPlainString()).isEqualTo("12.50");
        assertThat(found.createdAt()).isEqualTo(CREATED);
    }

    @Test
    void findAllByCategoryOrdersByName() {
        insert("Spear", WEAPONS);
        insert("Axe", WEAPONS);
        insert("Helmet", ARMOR);

        assertThat(items.findAll(Optional.of(WEAPONS), 100)).extracting(Item::name).containsExactly("Axe", "Spear");
        assertThat(items.findAll(Optional.empty(), 100)).extracting(Item::name).containsExactly("Axe", "Helmet", "Spear");
    }

    @Test
    void findAllRespectsLimit() {
        IntStream.range(0, 5).forEach(i -> insert("Nail " + i, WEAPONS));

        assertThat(items.findAll(Optional.empty(), 3)).extracting(Item::name).containsExactly("Nail 0", "Nail 1", "Nail 2");
    }

    @Test
    void unknownCategoryOnForeignKey() {
        var item = Item.create(
                new NewItem("Orphan", null, Money.of("1", "EUR"), new CategoryId(UUID.randomUUID())), CREATED);

        assertThatThrownBy(() -> items.insert(item)).isInstanceOf(UnknownCategoryException.class);
    }

    private void insert(String name, CategoryId category) {
        items.insert(Item.create(new NewItem(name, null, Money.of("1", "EUR"), category), CREATED));
    }
}
