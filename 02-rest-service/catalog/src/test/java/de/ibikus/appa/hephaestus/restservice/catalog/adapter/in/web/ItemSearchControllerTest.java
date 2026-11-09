package de.ibikus.appa.hephaestus.restservice.catalog.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import de.ibikus.appa.hephaestus.restservice.catalog.application.fakes.InMemoryCategoryRepository;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(ItemsController.class)
@Import({WebMapperImpl.class, ProblemDetailsAdvice.class, WebSliceFakes.class, ItemSearchControllerTest.Fakes.class})
class ItemSearchControllerTest {

    private static final String PROBLEMS = "https://hephaestus.ibikus.de/problems/";

    @TestConfiguration
    static class Fakes {

        @Bean
        InMemoryItemRepository items() {
            return new InMemoryItemRepository();
        }

        @Bean
        ItemService itemService(InMemoryItemRepository items, Clock clock) {
            return new ItemService(items, new InMemoryCategoryRepository(items), clock);
        }
    }

    @Autowired
    MockMvcTester mvc;

    @Autowired
    InMemoryItemRepository items;

    @BeforeEach
    void oneItem() {
        items.clear();
        var swords = new Category(CategoryId.newId(), "Swords", 0);
        items.insert(Item.create(new NewItem("Sword", null, Money.of("10", "EUR"), swords.id()), WebSliceFakes.NOW));
    }

    @Test
    void returnsPageEnvelope() {
        var result = mvc.get().uri("/api/items?page=0&size=5&sort=-price,name").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.content[0].name").isEqualTo("Sword");
        assertThat(result).bodyJson().extractingPath("$.size").isEqualTo(5);
        assertThat(result).bodyJson().extractingPath("$.totalElements").isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$.totalPages").isEqualTo(1);
    }

    @Test
    void unknownSortFieldIsABadRequest() {
        var result = mvc.get().uri("/api/items?sort=weight").exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo(PROBLEMS + "invalid-request");
    }

    @Test
    void pageSizeOutsideBoundsIsABadRequest() {
        assertThat(mvc.get().uri("/api/items?size=0")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(mvc.get().uri("/api/items?size=101")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void minPriceAboveMaxPriceIsABadRequest() {
        assertThat(mvc.get().uri("/api/items?minPrice=30&maxPrice=10")).hasStatus(HttpStatus.BAD_REQUEST);
    }
}
