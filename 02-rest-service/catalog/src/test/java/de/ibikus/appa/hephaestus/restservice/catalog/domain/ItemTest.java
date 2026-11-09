package de.ibikus.appa.hephaestus.restservice.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ItemTest {

    private static final CategoryId SWORDS = CategoryId.newId();
    private static final Instant NOW = Instant.parse("2026-11-09T10:00:00Z");

    @Test
    void createAssignsIdTimestampAndVersionZero() {
        var item = Item.create(new NewItem(" Bronze sword ", "Sharp", Money.of("120", "EUR"), SWORDS), NOW);

        assertThat(item.id()).isNotNull();
        assertThat(item.name()).isEqualTo("Bronze sword");
        assertThat(item.createdAt()).isEqualTo(NOW);
        assertThat(item.version()).isZero();
    }

    @Test
    void newItemValidatesFields() {
        assertThatThrownBy(() -> new NewItem("", null, Money.of("1", "EUR"), SWORDS))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new NewItem("x".repeat(201), null, Money.of("1", "EUR"), SWORDS))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new NewItem("Sword", "d".repeat(2001), Money.of("1", "EUR"), SWORDS))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new NewItem("Sword", null, null, SWORDS)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new NewItem("Sword", null, Money.of("1", "EUR"), null))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    void replaceKeepsIdentityTimestampAndVersion() {
        var original = Item.create(new NewItem("Sword", null, Money.of("1", "EUR"), SWORDS), NOW);
        var shields = CategoryId.newId();

        var replaced = original.replace(new NewItem("Shield", "Round", Money.of("2", "EUR"), shields));

        assertThat(replaced.id()).isEqualTo(original.id());
        assertThat(replaced.createdAt()).isEqualTo(NOW);
        assertThat(replaced.version()).isEqualTo(original.version());
        assertThat(replaced.name()).isEqualTo("Shield");
        assertThat(replaced.description()).isEqualTo("Round");
        assertThat(replaced.price()).isEqualTo(Money.of("2", "EUR"));
        assertThat(replaced.categoryId()).isEqualTo(shields);
    }
}
