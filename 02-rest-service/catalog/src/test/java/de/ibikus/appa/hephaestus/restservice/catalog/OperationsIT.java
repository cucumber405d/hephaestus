package de.ibikus.appa.hephaestus.restservice.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import de.ibikus.appa.hephaestus.restservice.catalog.adapter.out.persistence.IdempotencyCleanup;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** API versioning, contract publication and housekeeping on the full application. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainers.class)
class OperationsIT {

    private static final String PROBLEMS = "https://hephaestus.ibikus.de/problems/";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    IdempotencyCleanup cleanup;

    @Test
    void unsupportedApiVersionIsABadRequest() {
        var result = mvc.get().uri("/api/categories").header("API-Version", "2").exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo(PROBLEMS + "unsupported-api-version");
    }

    @Test
    void versionOneIsTheDefaultAndExplicit() {
        assertThat(mvc.get().uri("/api/categories")).hasStatusOk();
        assertThat(mvc.get().uri("/api/categories").header("API-Version", "1")).hasStatusOk();
    }

    @Test
    void handWrittenContractIsPublishedAndGeneratedDocsAreOff() {
        assertThat(mvc.get().uri("/catalog-api.yaml")).hasStatusOk().bodyText().contains("openapi: 3.1.0");
        assertThat(mvc.get().uri("/v3/api-docs")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void cleanupRemovesKeysOlderThan24Hours() {
        jdbc.update("insert into idempotency_key (key, request_hash, response_status, created_at)"
                + " values ('old-key', repeat('a', 64), 201, now() - interval '25 hours')");
        jdbc.update("insert into idempotency_key (key, request_hash, response_status, created_at)"
                + " values ('fresh-key', repeat('b', 64), 201, now())");

        cleanup.run();

        var keys = jdbc.queryForList(
                "select key from idempotency_key where key in ('old-key', 'fresh-key')", String.class);
        assertThat(keys).containsExactly("fresh-key");
    }
}
