package de.ibikus.appa.hephaestus.containersci.catalog.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import de.ibikus.appa.hephaestus.containersci.catalog.application.port.in.ManageItemsUseCase;
import de.ibikus.appa.hephaestus.containersci.catalog.domain.CategoryId;
import de.ibikus.appa.hephaestus.containersci.catalog.domain.Item;
import de.ibikus.appa.hephaestus.containersci.catalog.domain.ItemId;
import de.ibikus.appa.hephaestus.containersci.catalog.domain.NewItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(ItemsController.class)
@Import({ProblemDetailsAdvice.class, ProblemDetailsAdviceTest.FailingItems.class})
class ProblemDetailsAdviceTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class FailingItems {

        @Bean
        ManageItemsUseCase failingItems() {
            return new ManageItemsUseCase() {
                @Override
                public Item create(NewItem data) {
                    throw new IllegalStateException("secret");
                }

                @Override
                public Item get(ItemId id) {
                    throw new IllegalStateException("secret jdbc:postgresql://db:5432");
                }

                @Override
                public List<Item> list(Optional<CategoryId> category) {
                    throw new IllegalStateException("secret");
                }
            };
        }
    }

    @Autowired
    MockMvcTester mvc;

    @Test
    void unexpectedErrorIs500WithoutDetails() {
        var result = mvc.get().uri("/api/items/{id}", UUID.randomUUID()).exchange();

        assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.type")
                .isEqualTo("https://hephaestus.ibikus.de/problems/internal-error");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("secret");
    }
}
