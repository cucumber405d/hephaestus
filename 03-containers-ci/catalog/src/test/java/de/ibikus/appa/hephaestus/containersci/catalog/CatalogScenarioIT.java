package de.ibikus.appa.hephaestus.containersci.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** The whole service on a real PostgreSQL: create, read back, list by category. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainers.class)
class CatalogScenarioIT {

    private static final String ARMOR = "01900000-0000-7000-8000-000000000001";
    private static final String WEAPONS = "01900000-0000-7000-8000-000000000003";

    @Autowired
    MockMvcTester mvc;

    @Test
    void createGetAndListByCategory() {
        var created = mvc.post()
                .uri("/api/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Scenario sword","price":{"amount":"99.90","currency":"EUR"},"categoryId":"%s"}"""
                        .formatted(WEAPONS))
                .exchange();
        assertThat(created).hasStatus(HttpStatus.CREATED);
        var location = created.getResponse().getHeader("Location");

        assertThat(mvc.get().uri(location).exchange())
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.name")
                .isEqualTo("Scenario sword");
        assertThat(mvc.get().uri("/api/items?category={id}", WEAPONS).exchange())
                .bodyJson()
                .extractingPath("$[*].name")
                .asArray()
                .contains("Scenario sword");
        assertThat(mvc.get().uri("/api/items?category={id}", ARMOR).exchange())
                .bodyJson()
                .extractingPath("$[*].name")
                .asArray()
                .doesNotContain("Scenario sword");
    }
}
