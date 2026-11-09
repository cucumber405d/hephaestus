package de.ibikus.appa.hephaestus.restservice.catalog.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import de.ibikus.appa.hephaestus.restservice.catalog.application.fakes.InMemoryCategoryRepository;
import de.ibikus.appa.hephaestus.restservice.catalog.application.fakes.InMemoryItemRepository;
import de.ibikus.appa.hephaestus.restservice.catalog.application.service.CategoryService;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.CategoryId;
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

@WebMvcTest(CategoriesController.class)
@Import({WebMapperImpl.class, ProblemDetailsAdvice.class, CategoriesControllerTest.Fakes.class})
class CategoriesControllerTest {

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
        CategoryService categoryService(InMemoryCategoryRepository categories, InMemoryItemRepository items) {
            return new CategoryService(categories, items);
        }
    }

    @Autowired
    MockMvcTester mvc;

    @Autowired
    InMemoryItemRepository items;

    @Autowired
    InMemoryCategoryRepository categories;

    @BeforeEach
    void resetFakes() {
        items.clear();
        categories.clear();
    }

    private String create(String name) {
        var result = mvc.post()
                .uri("/api/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\"}")
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        var location = result.getResponse().getHeader("Location");
        return location.substring(location.lastIndexOf('/') + 1);
    }

    @Test
    void createReturns201WithLocationAndETag() {
        var result = mvc.post()
                .uri("/api/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Swords\"}")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED).hasHeader("ETag", "\"0\"");
        assertThat(result.getResponse().getHeader("Location")).matches(".*/api/categories/[0-9a-f-]{36}");
        assertThat(result).bodyJson().extractingPath("$.name").isEqualTo("Swords");
    }

    @Test
    void blankNameIsAValidationProblem() {
        var result = mvc.post()
                .uri("/api/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}")
                .exchange();

        assertThat(result)
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo(PROBLEMS + "invalid-request");
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("name");
    }

    @Test
    void malformedJsonIsAValidationProblem() {
        var result = mvc.post()
                .uri("/api/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo(PROBLEMS + "invalid-request");
    }

    @Test
    void duplicateNameIsAConflict() {
        create("Swords");

        var result = mvc.post()
                .uri("/api/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\" swords \"}")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo(PROBLEMS + "duplicate-category");
    }

    @Test
    void getReturnsCategoryWithETag() {
        var id = create("Shields");

        assertThat(mvc.get().uri("/api/categories/{id}", id))
                .hasStatusOk()
                .hasHeader("ETag", "\"0\"")
                .bodyJson()
                .extractingPath("$.id")
                .isEqualTo(id);
    }

    @Test
    void unknownCategoryIsNotFound() {
        var result = mvc.get().uri("/api/categories/{id}", UUID.randomUUID()).exchange();

        assertThat(result)
                .hasStatus(HttpStatus.NOT_FOUND)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo(PROBLEMS + "not-found");
    }

    @Test
    void listIsOrderedByName() {
        create("Shields");
        create("Axes");

        assertThat(mvc.get().uri("/api/categories"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[*].name")
                .asArray()
                .containsSubsequence("Axes", "Shields");
    }

    @Test
    void deleteReturns204() {
        var id = create("Hammers");

        assertThat(mvc.delete().uri("/api/categories/{id}", id)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.get().uri("/api/categories/{id}", id)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void deletingACategoryInUseIsAConflict() {
        var id = create("Helmets");
        items.markCategoryInUse(new CategoryId(UUID.fromString(id)));

        var result = mvc.delete().uri("/api/categories/{id}", id).exchange();

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo(PROBLEMS + "category-in-use");
    }
}
