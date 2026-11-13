package de.ibikus.appa.hephaestus.containersci.catalog.application.service;

import static de.ibikus.appa.hephaestus.containersci.catalog.application.fakes.InMemoryCategoryRepository.ARMOR;
import static de.ibikus.appa.hephaestus.containersci.catalog.application.fakes.InMemoryCategoryRepository.WEAPONS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.ibikus.appa.hephaestus.containersci.catalog.application.fakes.InMemoryCategoryRepository;
import de.ibikus.appa.hephaestus.containersci.catalog.application.fakes.InMemoryItemRepository;
import de.ibikus.appa.hephaestus.containersci.catalog.domain.CategoryId;
import de.ibikus.appa.hephaestus.containersci.catalog.domain.Item;
import de.ibikus.appa.hephaestus.containersci.catalog.domain.ItemId;
import de.ibikus.appa.hephaestus.containersci.catalog.domain.ItemNotFoundException;
import de.ibikus.appa.hephaestus.containersci.catalog.domain.Money;
import de.ibikus.appa.hephaestus.containersci.catalog.domain.NewItem;
import de.ibikus.appa.hephaestus.containersci.catalog.domain.UnknownCategoryException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ItemServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");

    private final InMemoryItemRepository items = new InMemoryItemRepository();
    private final ItemService service =
            new ItemService(items, new InMemoryCategoryRepository(), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void createRejectsUnknownCategory() {
        var data = newItem("Sword", new CategoryId(UUID.randomUUID()));

        assertThatThrownBy(() -> service.create(data)).isInstanceOf(UnknownCategoryException.class);
    }

    @Test
    void createStampsClockTime() {
        var item = service.create(newItem("Sword", WEAPONS));

        assertThat(item.createdAt()).isEqualTo(Instant.parse("2026-11-13T10:00:00Z"));
        assertThat(items.findById(item.id())).contains(item);
    }

    @Test
    void getMissingThrowsNotFound() {
        assertThatThrownBy(() -> service.get(ItemId.newId())).isInstanceOf(ItemNotFoundException.class);
    }

    @Test
    void listFiltersByCategoryAndSortsByName() {
        service.create(newItem("Spear", WEAPONS));
        service.create(newItem("Axe", WEAPONS));
        service.create(newItem("Helmet", ARMOR));

        assertThat(service.list(Optional.of(WEAPONS))).extracting(Item::name).containsExactly("Axe", "Spear");
        assertThat(service.list(Optional.empty())).extracting(Item::name).containsExactly("Axe", "Helmet", "Spear");
    }

    @Test
    void listIsCappedAt100() {
        IntStream.range(0, 101).forEach(i -> service.create(newItem("Nail %03d".formatted(i), WEAPONS)));

        assertThat(service.list(Optional.empty())).hasSize(100);
        assertThat(ItemService.LIST_LIMIT).isEqualTo(100);
    }

    private static NewItem newItem(String name, CategoryId category) {
        return new NewItem(name, null, Money.of("10.00", "EUR"), category);
    }
}
