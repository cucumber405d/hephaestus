package de.ibikus.appa.hephaestus.containersci.catalog.adapter.in.web;

import static de.ibikus.appa.hephaestus.containersci.catalog.application.fakes.InMemoryCategoryRepository.ARMOR;
import static de.ibikus.appa.hephaestus.containersci.catalog.application.fakes.InMemoryCategoryRepository.WEAPONS;
import static org.assertj.core.api.Assertions.assertThat;

import de.ibikus.appa.hephaestus.containersci.catalog.application.fakes.InMemoryItemRepository;
import de.ibikus.appa.hephaestus.containersci.catalog.domain.Item;
import de.ibikus.appa.hephaestus.containersci.catalog.domain.Money;
import de.ibikus.appa.hephaestus.containersci.catalog.domain.NewItem;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@WebMvcTest(ItemsController.class)
@Import({ProblemDetailsAdvice.class, WebSliceFakes.class})
class ItemsControllerTest {

    private static final String PROBLEMS = "https://hephaestus.ibikus.de/problems/";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    InMemoryItemRepository items;

    @BeforeEach
    void emptyStore() {
        items.clear();
    }

    @Test
    void createReturns201WithLocation() {
        var result = post("""
                {"name":"Bronze sword","price":{"amount":"12.5","currency":"EUR"},"categoryId":"%s"}"""
                .formatted(WEAPONS.value()));

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result.getResponse().getHeader("Location")).matches(".*/api/items/[0-9a-f-]{36}");
        assertThat(result).bodyJson().extractingPath("$.price.amount").isEqualTo("12.50");
        assertThat(result).bodyJson().extractingPath("$.createdAt").isEqualTo("2026-11-13T10:00:00Z");
    }

    @Test
    void createInvalidPriceIs400() {
        var result = post("""
                {"name":"Sword","price":{"amount":"12.345","currency":"EUR"},"categoryId":"%s"}"""
                .formatted(WEAPONS.value()));

        assertThat(result)
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo(PROBLEMS + "invalid-request");
    }

    @Test
    void createWithoutPriceIs400() {
        var result = post("""
                {"name":"Sword","categoryId":"%s"}""".formatted(WEAPONS.value()));

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo(PROBLEMS + "invalid-request");
    }

    @Test
    void createMalformedJsonIs400() {
        var result = post("{\"name\":");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo(PROBLEMS + "invalid-request");
    }

    @Test
    void createUnknownCategoryIs422() {
        var result = post("""
                {"name":"Sword","price":{"amount":"1","currency":"EUR"},"categoryId":"%s"}"""
                .formatted(UUID.randomUUID()));

        assertThat(result).hasStatus(422);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo(PROBLEMS + "unknown-category");
    }

    @Test
    void getReturnsItem() {
        var item = items.insert(
                Item.create(new NewItem("Helmet", "Bronze", Money.of("30", "EUR"), ARMOR), WebSliceFakes.NOW));

        var result = mvc.get().uri("/api/items/{id}", item.id().value()).exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.name").isEqualTo("Helmet");
        assertThat(result).bodyJson().extractingPath("$.categoryId").isEqualTo(ARMOR.value().toString());
    }

    @Test
    void getMissingIs404() {
        var result = mvc.get().uri("/api/items/{id}", UUID.randomUUID()).exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo(PROBLEMS + "not-found");
    }

    @Test
    void getMalformedIdIs400() {
        var result = mvc.get().uri("/api/items/not-a-uuid").exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo(PROBLEMS + "invalid-request");
    }

    @Test
    void listFiltersByCategory() {
        items.insert(Item.create(new NewItem("Axe", null, Money.of("5", "EUR"), WEAPONS), WebSliceFakes.NOW));
        items.insert(Item.create(new NewItem("Shield", null, Money.of("5", "EUR"), ARMOR), WebSliceFakes.NOW));

        var result = mvc.get().uri("/api/items?category={id}", WEAPONS.value()).exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$[*].name").asArray().containsExactly("Axe");
    }

    @Test
    void listMalformedCategoryIs400() {
        var result = mvc.get().uri("/api/items?category=not-a-uuid").exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo(PROBLEMS + "invalid-request");
    }

    private MvcTestResult post(String body) {
        return mvc.post()
                .uri("/api/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }
}
