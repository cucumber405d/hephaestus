package de.ibikus.appa.hephaestus.restservice.catalog.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.ibikus.appa.hephaestus.restservice.catalog.application.fakes.InMemoryCategoryRepository;
import de.ibikus.appa.hephaestus.restservice.catalog.application.fakes.InMemoryItemRepository;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.Category;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.CategoryId;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.CategoryInUseException;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.CategoryNotFoundException;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.DuplicateCategoryNameException;
import org.junit.jupiter.api.Test;

class CategoryServiceTest {

    private final InMemoryItemRepository items = new InMemoryItemRepository();
    private final InMemoryCategoryRepository categories = new InMemoryCategoryRepository(items);
    private final CategoryService service = new CategoryService(categories, items);

    @Test
    void createsCategoryWithStrippedName() {
        var created = service.create("  Swords ");

        assertThat(created.name()).isEqualTo("Swords");
        assertThat(created.version()).isZero();
        assertThat(service.get(created.id())).isEqualTo(created);
    }

    @Test
    void rejectsDuplicateNameIgnoringCaseAndSpaces() {
        service.create("Swords");

        assertThatThrownBy(() -> service.create(" swords ")).isInstanceOf(DuplicateCategoryNameException.class);
    }

    @Test
    void listsCategoriesByName() {
        service.create("Shields");
        service.create("Axes");

        assertThat(service.list()).extracting(Category::name).containsExactly("Axes", "Shields");
    }

    @Test
    void unknownCategoryIsNotFound() {
        assertThatThrownBy(() -> service.get(CategoryId.newId())).isInstanceOf(CategoryNotFoundException.class);
        assertThatThrownBy(() -> service.delete(CategoryId.newId())).isInstanceOf(CategoryNotFoundException.class);
    }

    @Test
    void categoryWithItemsCannotBeDeleted() {
        var swords = service.create("Swords");
        items.markCategoryInUse(swords.id());

        assertThatThrownBy(() -> service.delete(swords.id())).isInstanceOf(CategoryInUseException.class);
    }

    @Test
    void deletesUnusedCategory() {
        var swords = service.create("Swords");

        service.delete(swords.id());

        assertThat(service.list()).isEmpty();
    }
}
