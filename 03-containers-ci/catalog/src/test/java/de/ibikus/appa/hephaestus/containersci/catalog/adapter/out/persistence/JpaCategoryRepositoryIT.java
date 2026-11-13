package de.ibikus.appa.hephaestus.containersci.catalog.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import de.ibikus.appa.hephaestus.containersci.catalog.PostgresTestcontainers;
import de.ibikus.appa.hephaestus.containersci.catalog.domain.Category;
import de.ibikus.appa.hephaestus.containersci.catalog.domain.CategoryId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestcontainers.class, JpaCategoryRepository.class})
class JpaCategoryRepositoryIT {

    @Autowired
    JpaCategoryRepository categories;

    @Test
    void existsSeededCategory() {
        assertThat(categories.exists(JpaItemRepositoryIT.WEAPONS)).isTrue();
        assertThat(categories.exists(new CategoryId(UUID.randomUUID()))).isFalse();
    }

    @Test
    void listsSeedByName() {
        assertThat(categories.findAllOrderedByName())
                .extracting(Category::name)
                .containsExactly("Armor", "Tools", "Weapons");
    }
}
