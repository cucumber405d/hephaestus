package de.ibikus.appa.hephaestus.containersci.catalog.adapter.in.web;

import de.ibikus.appa.hephaestus.containersci.catalog.application.fakes.InMemoryCategoryRepository;
import de.ibikus.appa.hephaestus.containersci.catalog.application.fakes.InMemoryItemRepository;
import de.ibikus.appa.hephaestus.containersci.catalog.application.service.CategoryService;
import de.ibikus.appa.hephaestus.containersci.catalog.application.service.ItemService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** Real services on in-memory repositories for web-slice tests. */
@TestConfiguration(proxyBeanMethods = false)
class WebSliceFakes {

    static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");

    @Bean
    InMemoryItemRepository items() {
        return new InMemoryItemRepository();
    }

    @Bean
    InMemoryCategoryRepository categories() {
        return new InMemoryCategoryRepository();
    }

    @Bean
    ItemService itemService(InMemoryItemRepository items, InMemoryCategoryRepository categories) {
        return new ItemService(items, categories, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Bean
    CategoryService categoryService(InMemoryCategoryRepository categories) {
        return new CategoryService(categories);
    }
}
