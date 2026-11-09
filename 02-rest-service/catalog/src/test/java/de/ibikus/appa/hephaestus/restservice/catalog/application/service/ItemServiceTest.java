package de.ibikus.appa.hephaestus.restservice.catalog.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.ibikus.appa.hephaestus.restservice.catalog.application.fakes.InMemoryCategoryRepository;
import de.ibikus.appa.hephaestus.restservice.catalog.application.fakes.InMemoryItemRepository;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.Category;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.CategoryId;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.ItemId;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.ItemNotFoundException;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.Money;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.NewItem;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.UnknownCategoryException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ItemServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-09T10:00:00.123456789Z");

    private final InMemoryItemRepository items = new InMemoryItemRepository();
    private final InMemoryCategoryRepository categories = new InMemoryCategoryRepository(items);
    private final ItemService service = new ItemService(items, categories, Clock.fixed(NOW, ZoneOffset.UTC));
    private final Category swords = categories.insert(new Category(CategoryId.newId(), "Swords", 0));

    @Test
    void createsItemInExistingCategory() {
        var item = service.create(new NewItem("Bronze sword", "Sharp", Money.of("120", "EUR"), swords.id()));

        assertThat(item.version()).isZero();
        assertThat(item.categoryId()).isEqualTo(swords.id());
        assertThat(service.get(item.id())).isEqualTo(item);
    }

    @Test
    void creationTimeIsTruncatedToMicrosecondsLikeTheDatabase() {
        var item = service.create(new NewItem("Bronze sword", null, Money.of("120", "EUR"), swords.id()));

        assertThat(item.createdAt()).isEqualTo(Instant.parse("2026-11-09T10:00:00.123456Z"));
    }

    @Test
    void rejectsUnknownCategory() {
        assertThatThrownBy(() -> service.create(new NewItem("Sword", null, Money.of("1", "EUR"), CategoryId.newId())))
                .isInstanceOf(UnknownCategoryException.class);
    }

    @Test
    void unknownItemIsNotFound() {
        assertThatThrownBy(() -> service.get(ItemId.newId())).isInstanceOf(ItemNotFoundException.class);
    }
}
