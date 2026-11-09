package de.ibikus.appa.hephaestus.restservice.catalog.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import de.ibikus.appa.hephaestus.restservice.catalog.application.fakes.InMemoryCategoryRepository;
import de.ibikus.appa.hephaestus.restservice.catalog.application.fakes.InMemoryIdempotencyStore;
import de.ibikus.appa.hephaestus.restservice.catalog.application.fakes.InMemoryItemRepository;
import de.ibikus.appa.hephaestus.restservice.catalog.application.service.ItemService;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.Category;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.CategoryId;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@WebMvcTest(ItemsController.class)
@Import({WebMapperImpl.class, ProblemDetailsAdvice.class, WebSliceFakes.class, ItemsControllerTest.Fakes.class})
class ItemsControllerTest {

    private static final String PROBLEMS = "https://hephaestus.ibikus.de/problems/";

    @TestConfiguration
    static class Fakes {

        @Bean
        InMemoryItemRepository items() {
            return new InMemoryItemRepository();
        }

        @Bean
        InMemoryCategoryRepository categories(InMemoryItemRepository items) {
            return new InMemoryCategoryRepository(items);
        }

        @Bean
        ItemService itemService(
                InMemoryItemRepository items,
                InMemoryCategoryRepository categories,
                InMemoryIdempotencyStore idempotency,
                Clock clock) {
            return new ItemService(items, categories, idempotency, clock);
        }
    }

    @Autowired
    MockMvcTester mvc;

    @Autowired
    InMemoryItemRepository items;

    @Autowired
    InMemoryCategoryRepository categories;

    @Autowired
    InMemoryIdempotencyStore idempotency;

    private Category swords;

    @BeforeEach
    void resetFakes() {
        items.clear();
        categories.clear();
        idempotency.clear();
        swords = categories.insert(new Category(CategoryId.newId(), "Swords", 0));
    }

    private MvcTestResult post(String body) {
        return mvc.post()
                .uri("/api/items")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private String item(String category, String amount) {
        return """
                {"name":"Bronze sword","description":"Sharp","price":{"amount":"%s","currency":"EUR"},"categoryId":"%s"}""".formatted(amount, category);
    }

    @Test
    void createReturns201WithLocationETagAndNormalizedPrice() {
        var result = post(item(swords.id().value().toString(), "120"));

        assertThat(result).hasStatus(HttpStatus.CREATED).hasHeader("ETag", "\"0\"");
        assertThat(result.getResponse().getHeader("Location")).matches(".*/api/items/[0-9a-f-]{36}");
        assertThat(result).bodyJson().extractingPath("$.price.amount").isEqualTo("120.00");
        assertThat(result).bodyJson().extractingPath("$.createdAt").asString().startsWith("2026-11-09T10:00:00.123456");
    }

    @Test
    void unknownCategoryIsUnprocessable() {
        var result = post(item(UUID.randomUUID().toString(), "1"));

        assertThat(result).hasStatus(HttpStatus.valueOf(422));
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo(PROBLEMS + "unknown-category");
    }

    @Test
    void priceWithMoreThanTenIntegerDigitsIsRejected() {
        var result = post(item(swords.id().value().toString(), "10000000000"));

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo(PROBLEMS + "invalid-request");
    }

    @Test
    void priceWithThreeFractionDigitsIsRejected() {
        var result = post(item(swords.id().value().toString(), "12.345"));

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo(PROBLEMS + "invalid-request");
    }

    @Test
    void getReturnsItemWithETag() {
        var location =
                post(item(swords.id().value().toString(), "5")).getResponse().getHeader("Location");

        assertThat(mvc.get().uri(location)).hasStatusOk().hasHeader("ETag", "\"0\"");
    }

    @Test
    void unknownItemIsNotFound() {
        var result = mvc.get().uri("/api/items/{id}", UUID.randomUUID()).exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo(PROBLEMS + "not-found");
    }
}
