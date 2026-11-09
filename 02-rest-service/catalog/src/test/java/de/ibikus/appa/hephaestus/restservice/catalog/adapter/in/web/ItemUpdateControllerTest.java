package de.ibikus.appa.hephaestus.restservice.catalog.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import de.ibikus.appa.hephaestus.restservice.catalog.application.fakes.InMemoryCategoryRepository;
import de.ibikus.appa.hephaestus.restservice.catalog.application.fakes.InMemoryIdempotencyStore;
import de.ibikus.appa.hephaestus.restservice.catalog.application.fakes.InMemoryItemRepository;
import de.ibikus.appa.hephaestus.restservice.catalog.application.service.ItemService;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.Category;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.CategoryId;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.Item;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.Money;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.NewItem;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
@Import({WebMapperImpl.class, ProblemDetailsAdvice.class, WebSliceFakes.class, ItemUpdateControllerTest.Fakes.class})
class ItemUpdateControllerTest {

    private static final String PROBLEMS = "https://hephaestus.ibikus.de/problems/";
    private static final MediaType MERGE_PATCH = MediaType.parseMediaType("application/merge-patch+json");

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

    private Category swords;
    private String uri;

    @BeforeEach
    void oneItem() {
        items.clear();
        categories.clear();
        swords = categories.insert(new Category(CategoryId.newId(), "Swords", 0));
        Item sword = items.insert(
                Item.create(new NewItem("Sword", "Sharp", Money.of("120", "EUR"), swords.id()), WebSliceFakes.NOW));
        uri = "/api/items/" + sword.id().value();
    }

    private String replacement() {
        return """
                {"name":"Axe","price":{"amount":"80","currency":"EUR"},"categoryId":"%s"}""".formatted(swords.id().value());
    }

    private MvcTestResult put(String ifMatch) {
        var request = mvc.put().uri(uri).contentType(MediaType.APPLICATION_JSON).content(replacement());
        if (ifMatch != null) {
            request = request.header("If-Match", ifMatch);
        }
        return request.exchange();
    }

    private MvcTestResult patch(String body) {
        return mvc.patch()
                .uri(uri)
                .header("If-Match", "\"0\"")
                .contentType(MERGE_PATCH)
                .content(body)
                .exchange();
    }

    @Test
    void putWithoutIfMatchIsPreconditionRequired() {
        var result = put(null);

        assertThat(result).hasStatus(HttpStatus.PRECONDITION_REQUIRED);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo(PROBLEMS + "precondition-required");
    }

    @Test
    void putWithStaleETagIsPreconditionFailed() {
        var result = put("\"5\"");

        assertThat(result).hasStatus(HttpStatus.PRECONDITION_FAILED);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo(PROBLEMS + "precondition-failed");
    }

    @ParameterizedTest
    @ValueSource(strings = {"*", "W/\"0\"", "\"abc\""})
    void putWithUnusableIfMatchIsPreconditionFailed(String ifMatch) {
        assertThat(put(ifMatch)).hasStatus(HttpStatus.PRECONDITION_FAILED);
    }

    @Test
    void putWithCurrentETagReplacesAndReturnsNewETag() {
        var result = put("\"0\"");

        assertThat(result).hasStatusOk().hasHeader("ETag", "\"1\"");
        assertThat(result).bodyJson().extractingPath("$.name").isEqualTo("Axe");
        assertThat(result).bodyJson().doesNotHavePath("$.description");
    }

    @Test
    void patchWithPlainJsonIsUnsupportedMediaType() {
        var result = mvc.patch()
                .uri(uri)
                .header("If-Match", "\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Axe\"}")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo(PROBLEMS + "unsupported-media-type");
    }

    @Test
    void patchRemovesDescriptionWithNull() {
        var result = patch("{\"description\":null}");

        assertThat(result).hasStatusOk().hasHeader("ETag", "\"1\"");
        assertThat(result).bodyJson().doesNotHavePath("$.description");
        assertThat(result).bodyJson().extractingPath("$.name").isEqualTo("Sword");
    }

    @Test
    void patchMergesPriceAmountAndKeepsCurrency() {
        var result = patch("{\"price\":{\"amount\":\"9.99\"}}");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.price.amount").isEqualTo("9.99");
        assertThat(result).bodyJson().extractingPath("$.price.currency").isEqualTo("EUR");
    }

    @Test
    void patchCannotRemoveRequiredFieldsOrTouchReadOnlyOnes() {
        assertThat(patch("{\"name\":null}")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(patch("{\"id\":\"00000000-0000-0000-0000-000000000000\"}")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void patchWithoutIfMatchIsPreconditionRequired() {
        var result = mvc.patch()
                .uri(uri)
                .contentType(MERGE_PATCH)
                .content("{\"name\":\"Axe\"}")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.PRECONDITION_REQUIRED);
    }

    @Test
    void deleteRequiresCurrentETag() {
        assertThat(mvc.delete().uri(uri)).hasStatus(HttpStatus.PRECONDITION_REQUIRED);
        assertThat(mvc.delete().uri(uri).header("If-Match", "\"7\"")).hasStatus(HttpStatus.PRECONDITION_FAILED);
        assertThat(mvc.delete().uri(uri).header("If-Match", "\"0\"")).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.get().uri(uri)).hasStatus(HttpStatus.NOT_FOUND);
    }
}
