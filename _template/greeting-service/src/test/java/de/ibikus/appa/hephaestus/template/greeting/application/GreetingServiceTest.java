package de.ibikus.appa.hephaestus.template.greeting.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GreetingServiceTest {

    private final GreetingService service = new GreetingService();

    @Test
    void greetsRecipient() {
        assertThat(service.greet("Hephaestus").message()).isEqualTo("Hello, Hephaestus!");
    }
}
