package de.ibikus.appa.hephaestus.template.greeting.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class GreetingTest {

    @Test
    void buildsMessageForRecipient() {
        assertThat(new Greeting("Hephaestus").message()).isEqualTo("Hello, Hephaestus!");
    }

    @Test
    void rejectsBlankRecipient() {
        assertThatThrownBy(() -> new Greeting(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullRecipient() {
        assertThatThrownBy(() -> new Greeting(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsRecipientOfMaxLength() {
        assertThat(new Greeting("x".repeat(50)).recipient()).hasSize(50);
    }

    @Test
    void rejectsTooLongRecipient() {
        assertThatThrownBy(() -> new Greeting("x".repeat(51))).isInstanceOf(IllegalArgumentException.class);
    }
}
