package de.ibikus.appa.hephaestus.containersci.catalog.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import de.ibikus.appa.hephaestus.containersci.catalog.application.fakes.InMemoryCategoryRepository;
import de.ibikus.appa.hephaestus.containersci.catalog.domain.Category;
import org.junit.jupiter.api.Test;

class CategoryServiceTest {

    @Test
    void listsByName() {
        var service = new CategoryService(new InMemoryCategoryRepository());

        assertThat(service.list()).extracting(Category::name).containsExactly("Armor", "Tools", "Weapons");
    }
}
