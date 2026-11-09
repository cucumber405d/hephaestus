package de.ibikus.appa.hephaestus.restservice.catalog.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.ibikus.appa.hephaestus.restservice.catalog.PostgresTestcontainers;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.Category;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.CategoryId;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.Item;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.Money;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.NewItem;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The race the If-Match check cannot see: two transactions read version 0, the second commits first. The first
 * one's flush must fail on the version column instead of silently overwriting (lost update).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestcontainers.class, JpaCategoryRepository.class, JpaItemRepository.class, PersistenceMapperImpl.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OptimisticLockingIT {

    @Autowired
    JpaCategoryRepository categories;

    @Autowired
    JpaItemRepository items;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Test
    void secondWriterOfTheSameVersionFails() {
        var outer = new TransactionTemplate(transactionManager);
        var inner = new TransactionTemplate(transactionManager);
        inner.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        var sword = outer.execute(status -> {
            var category = categories.insert(new Category(CategoryId.newId(), "Lock " + UUID.randomUUID(), 0));
            return items.insert(Item.create(
                    new NewItem("Sword", null, Money.of("10", "EUR"), category.id()),
                    Instant.parse("2026-11-09T10:00:00Z")));
        });

        assertThatThrownBy(() -> outer.executeWithoutResult(status -> {
                    var mine = items.findById(sword.id()).orElseThrow(); // version 0 is now managed here
                    inner.executeWithoutResult(other -> items.update(
                            sword.replace(new NewItem("Axe", null, Money.of("20", "EUR"), sword.categoryId()))));
                    items.update(mine.replace(new NewItem("Mace", null, Money.of("30", "EUR"), sword.categoryId())));
                }))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        assertThat(items.findById(sword.id()).orElseThrow().name()).isEqualTo("Axe");
    }
}
