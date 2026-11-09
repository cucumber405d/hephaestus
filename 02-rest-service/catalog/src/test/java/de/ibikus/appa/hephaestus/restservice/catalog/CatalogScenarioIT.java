package de.ibikus.appa.hephaestus.restservice.catalog;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

/**
 * A whole client journey through the real service and database. Every request and response is validated against
 * catalog-api.yaml: status codes, headers and bodies the generator cannot check at compile time.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainers.class)
class CatalogScenarioIT {

    private static final String CONTRACT = "static/catalog-api.yaml";
    private static final ResultMatcher MATCHES_CONTRACT = openApi().isValid(CONTRACT);

    @Autowired
    MockMvc mvc;

    @Test
    void clientJourneyFollowsTheContract() throws Exception {
        var categoryLocation = mvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Scenario " + UUID.randomUUID() + "\"}"))
                // contract check first: the mutation check (200 instead of 201) must be caught by the validator
                .andExpect(MATCHES_CONTRACT)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getHeader("Location");
        var categoryId = categoryLocation.substring(categoryLocation.lastIndexOf('/') + 1);

        var itemLocation = mvc.perform(post("/api/items")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Scenario sword","description":"Sharp","price":{"amount":"120","currency":"EUR"},"categoryId":"%s"}""".formatted(categoryId)))
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(MATCHES_CONTRACT)
                .andReturn()
                .getResponse()
                .getHeader("Location");
        var itemPath = itemLocation.substring(itemLocation.indexOf("/api/"));

        mvc.perform(get("/api/items")
                        .param("category", categoryId)
                        .param("q", "scenario")
                        .param("sort", "-price,name"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(MATCHES_CONTRACT);

        mvc.perform(patch(itemPath)
                        .header("If-Match", "\"0\"")
                        .contentType("application/merge-patch+json")
                        .content("{\"description\":null,\"price\":{\"amount\":\"99.90\"}}"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(MATCHES_CONTRACT);

        mvc.perform(put(itemPath)
                        .header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Stale","price":{"amount":"1","currency":"EUR"},"categoryId":"%s"}""".formatted(categoryId)))
                .andExpect(status().isPreconditionFailed())
                .andExpect(MATCHES_CONTRACT);

        mvc.perform(delete(itemPath).header("If-Match", "\"1\""))
                .andExpect(status().isNoContent())
                .andExpect(MATCHES_CONTRACT);

        mvc.perform(get(itemPath)).andExpect(status().isNotFound()).andExpect(MATCHES_CONTRACT);
    }
}
