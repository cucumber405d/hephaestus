package de.ibikus.appa.hephaestus.containersci.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ItemTest {

    private static final CategoryId CATEGORY = new CategoryId(UUID.randomUUID());
    private static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");

    @Test
    void createAssignsIdAndTimestampAndStripsName() {
        var item = Item.create(new NewItem("  Hammer ", null, Money.of("10", "EUR"), CATEGORY), NOW);

        assertThat(item.id()).isNotNull();
        assertThat(item.name()).isEqualTo("Hammer");
        assertThat(item.createdAt()).isEqualTo(NOW);
        assertThat(item.description()).isNull();
    }

    @Test
    void rejectsBlankName() {
        assertThatThrownBy(() -> new NewItem(" ", null, Money.of("1", "EUR"), CATEGORY))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    void rejectsTooLongName() {
        assertThatThrownBy(() -> new NewItem("x".repeat(201), null, Money.of("1", "EUR"), CATEGORY))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    void rejectsMissingPriceAndCategory() {
        assertThatThrownBy(() -> new NewItem("Hammer", null, null, CATEGORY)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new NewItem("Hammer", null, Money.of("1", "EUR"), null))
                .isInstanceOf(InvalidValueException.class);
    }
}
