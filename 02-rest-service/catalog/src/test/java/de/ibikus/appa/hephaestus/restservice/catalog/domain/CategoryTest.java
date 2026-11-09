package de.ibikus.appa.hephaestus.restservice.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CategoryTest {

    @Test
    void stripsName() {
        assertThat(new Category(CategoryId.newId(), "  Swords ", 0).name()).isEqualTo("Swords");
    }

    @Test
    void rejectsBlankOrTooLongName() {
        assertThatThrownBy(() -> new Category(CategoryId.newId(), "   ", 0)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new Category(CategoryId.newId(), "x".repeat(101), 0))
                .isInstanceOf(InvalidValueException.class);
        assertThat(new Category(CategoryId.newId(), "x".repeat(100), 0).name()).hasSize(100);
    }
}
