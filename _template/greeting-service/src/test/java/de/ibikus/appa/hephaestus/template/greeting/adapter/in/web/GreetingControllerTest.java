package de.ibikus.appa.hephaestus.template.greeting.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import de.ibikus.appa.hephaestus.template.greeting.application.GreetingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(GreetingController.class)
@Import(GreetingService.class)
class GreetingControllerTest {

    @Autowired
    MockMvcTester mvc;

    @Test
    void returnsGreeting() {
        assertThat(mvc.get().uri("/api/greetings/Hephaestus"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.message")
                .isEqualTo("Hello, Hephaestus!");
    }

    @Test
    void rejectsTooLongRecipientWithProblemDetail() {
        assertThat(mvc.get().uri("/api/greetings/" + "x".repeat(51)))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.title")
                .isEqualTo("Invalid recipient");
    }
}
