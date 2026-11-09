package de.ibikus.appa.hephaestus.restservice.catalog.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import de.ibikus.appa.hephaestus.restservice.catalog.PostgresTestcontainers;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.Category;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.CategoryId;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.Item;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.ItemSearchCriteria;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.Money;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.NewItem;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.PageRequest;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.SortOrder;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestcontainers.class, JpaCategoryRepository.class, JpaItemRepository.class, PersistenceMapperImpl.class
})
class ItemSearchIT {

    private static final Instant NOW = Instant.parse("2026-11-09T10:00:00Z");
    private static final PageRequest FIRST_PAGE = new PageRequest(0, 20);

    @Autowired
    JpaCategoryRepository categories;

    @Autowired
    JpaItemRepository items;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    private Category weapons;

    @BeforeEach
    void catalog() {
        weapons = categories.insert(new Category(CategoryId.newId(), "Weapons", 0));
        var armor = categories.insert(new Category(CategoryId.newId(), "Armor", 0));
        add("Бронзовый меч", "50", weapons);
        add("Iron sword", "20", weapons);
        add("Steel axe", "30", weapons);
        add("Round shield", "10", armor);
        add("Helmet", "5", armor);
    }

    private void add(String name, String price, Category category) {
        items.insert(Item.create(new NewItem(name, null, Money.of(price, "EUR"), category.id()), NOW));
    }

    private List<String> names(ItemSearchCriteria criteria) {
        return items.search(criteria, FIRST_PAGE).content().stream()
                .map(Item::name)
                .toList();
    }

    private static ItemSearchCriteria criteria(String q, String... sort) {
        return new ItemSearchCriteria(
                null,
                null,
                null,
                q,
                List.of(sort).stream().map(SortOrder::parse).toList());
    }

    @Test
    void filtersByCategory() {
        assertThat(names(new ItemSearchCriteria(weapons.id(), null, null, null, null)))
                .containsExactlyInAnyOrder("Бронзовый меч", "Iron sword", "Steel axe");
    }

    @Test
    void priceRangeIsInclusive() {
        assertThat(names(new ItemSearchCriteria(null, new BigDecimal("10"), new BigDecimal("30"), null, null)))
                .containsExactlyInAnyOrder("Iron sword", "Steel axe", "Round shield");
    }

    @Test
    void nameSearchIgnoresCaseIncludingCyrillic() {
        assertThat(names(criteria("БРОНЗ"))).containsExactly("Бронзовый меч");
        assertThat(names(criteria("SWORD"))).containsExactly("Iron sword");
    }

    @Test
    void wildcardCharactersMatchLiterally() {
        assertThat(names(criteria("%"))).isEmpty();
        assertThat(names(criteria("_"))).isEmpty();
    }

    @Test
    void sortsByPriceDescending() {
        var prices = items.search(criteria(null, "-price"), FIRST_PAGE).content().stream()
                .map(item -> item.price().amount().toPlainString())
                .toList();

        assertThat(prices).containsExactly("50.00", "30.00", "20.00", "10.00", "5.00");
    }

    @Test
    void sortsByNameByDefault() {
        assertThat(names(criteria(null)))
                .containsExactly("Helmet", "Iron sword", "Round shield", "Steel axe", "Бронзовый меч");
    }

    @Test
    void paginates() {
        var page = items.search(criteria(null), new PageRequest(1, 2));

        assertThat(page.content()).extracting(Item::name).containsExactly("Round shield", "Steel axe");
        assertThat(page.totalElements()).isEqualTo(5);
        assertThat(page.totalPages()).isEqualTo(3);
    }

    /** One select for the page and one count: no N+1, because items carry the category id, not the category. */
    @Test
    void aPageCostsExactlyTwoStatements() {
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        items.search(criteria(null), new PageRequest(0, 2));

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    }
}
