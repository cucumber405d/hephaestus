package de.ibikus.appa.hephaestus.restservice.catalog.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.ibikus.appa.hephaestus.restservice.catalog.application.fakes.InMemoryCategoryRepository;
import de.ibikus.appa.hephaestus.restservice.catalog.application.fakes.InMemoryIdempotencyStore;
import de.ibikus.appa.hephaestus.restservice.catalog.application.fakes.InMemoryItemRepository;
import de.ibikus.appa.hephaestus.restservice.catalog.application.port.in.IdempotencyKey;
import de.ibikus.appa.hephaestus.restservice.catalog.application.port.in.ItemPatch;
import de.ibikus.appa.hephaestus.restservice.catalog.application.port.in.PatchValue;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.Category;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.CategoryId;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.IdempotencyConflictException;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.Item;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.ItemId;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.ItemNotFoundException;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.Money;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.NewItem;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.UnknownCategoryException;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.VersionConflictException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ItemServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-09T10:00:00.123456789Z");

    private final InMemoryItemRepository items = new InMemoryItemRepository();
    private final InMemoryCategoryRepository categories = new InMemoryCategoryRepository(items);
    private final InMemoryIdempotencyStore idempotency = new InMemoryIdempotencyStore();
    private final ItemService service =
            new ItemService(items, categories, idempotency, Clock.fixed(NOW, ZoneOffset.UTC));
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
    void retryWithSameKeyAndDataReturnsTheFirstItem() {
        var key = new IdempotencyKey("key-1");
        var data = new NewItem("Bronze sword", "Sharp", Money.of("120", "EUR"), swords.id());

        var first = service.create(data, key);
        var retry = service.create(new NewItem("Bronze sword", "Sharp", Money.of("120.00", "EUR"), swords.id()), key);

        assertThat(first.replayed()).isFalse();
        assertThat(retry.replayed()).isTrue();
        assertThat(retry.item()).isEqualTo(first.item());
        assertThat(items.size()).isEqualTo(1);
    }

    @Test
    void sameKeyWithDifferentDataIsAConflict() {
        var key = new IdempotencyKey("key-2");
        service.create(new NewItem("Bronze sword", null, Money.of("120", "EUR"), swords.id()), key);

        assertThatThrownBy(
                        () -> service.create(new NewItem("Iron sword", null, Money.of("120", "EUR"), swords.id()), key))
                .isInstanceOf(IdempotencyConflictException.class);
        assertThat(items.size()).isEqualTo(1);
    }

    @Test
    void replayOfADeletedItemIsAConflict() {
        var key = new IdempotencyKey("key-3");
        var created = service.create(new NewItem("Bronze sword", null, Money.of("120", "EUR"), swords.id()), key);
        service.delete(created.item().id(), 0);

        assertThatThrownBy(() ->
                        service.create(new NewItem("Bronze sword", null, Money.of("120", "EUR"), swords.id()), key))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void failedCreationDoesNotConsumeTheKey() {
        var key = new IdempotencyKey("key-4");
        assertThatThrownBy(
                        () -> service.create(new NewItem("Sword", null, Money.of("1", "EUR"), CategoryId.newId()), key))
                .isInstanceOf(UnknownCategoryException.class);

        assertThat(idempotency.find("key-4")).isEmpty();
    }

    @Test
    void unknownItemIsNotFound() {
        assertThatThrownBy(() -> service.get(ItemId.newId())).isInstanceOf(ItemNotFoundException.class);
    }

    private Item newSword() {
        return service.create(new NewItem("Sword", "Sharp", Money.of("120", "EUR"), swords.id()));
    }

    @Test
    void replaceWithCurrentVersionChangesFieldsAndBumpsVersion() {
        var sword = newSword();

        var replaced = service.replace(sword.id(), 0, new NewItem("Axe", null, Money.of("80", "EUR"), swords.id()));

        assertThat(replaced.name()).isEqualTo("Axe");
        assertThat(replaced.description()).isNull();
        assertThat(replaced.version()).isEqualTo(1);
    }

    @Test
    void replaceWithStaleVersionIsAConflict() {
        var sword = newSword();

        assertThatThrownBy(() ->
                        service.replace(sword.id(), 5, new NewItem("Axe", null, Money.of("80", "EUR"), swords.id())))
                .isInstanceOf(VersionConflictException.class);
    }

    @Test
    void replaceIntoUnknownCategoryIsRejected() {
        var sword = newSword();

        assertThatThrownBy(() -> service.replace(
                        sword.id(), 0, new NewItem("Axe", null, Money.of("80", "EUR"), CategoryId.newId())))
                .isInstanceOf(UnknownCategoryException.class);
    }

    @Test
    void patchMergesOnlyGivenFields() {
        var sword = newSword();
        var patch = new ItemPatch(
                PatchValue.absent(),
                PatchValue.remove(),
                PatchValue.set("9.99"),
                PatchValue.absent(),
                PatchValue.absent());

        var patched = service.patch(sword.id(), 0, patch);

        assertThat(patched.name()).isEqualTo("Sword");
        assertThat(patched.description()).isNull();
        assertThat(patched.price()).isEqualTo(Money.of("9.99", "EUR"));
        assertThat(patched.version()).isEqualTo(1);
    }

    @Test
    void patchWithStaleVersionIsAConflict() {
        var sword = newSword();
        var rename = new ItemPatch(
                PatchValue.set("Axe"),
                PatchValue.absent(),
                PatchValue.absent(),
                PatchValue.absent(),
                PatchValue.absent());

        assertThatThrownBy(() -> service.patch(sword.id(), 1, rename)).isInstanceOf(VersionConflictException.class);
    }

    @Test
    void deleteChecksVersionAndRemoves() {
        var sword = newSword();

        assertThatThrownBy(() -> service.delete(sword.id(), 3)).isInstanceOf(VersionConflictException.class);
        service.delete(sword.id(), 0);
        assertThatThrownBy(() -> service.get(sword.id())).isInstanceOf(ItemNotFoundException.class);
    }

    @Test
    void changingAnUnknownItemIsNotFound() {
        assertThatThrownBy(() -> service.delete(ItemId.newId(), 0)).isInstanceOf(ItemNotFoundException.class);
    }
}
