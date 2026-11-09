package de.ibikus.appa.hephaestus.restservice.catalog.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import de.ibikus.appa.hephaestus.restservice.catalog.PostgresTestcontainers;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Idempotency-Key behaviour through the full filter chain against PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainers.class)
class IdempotencyIT {

    private static final String PROBLEMS = "https://hephaestus.ibikus.de/problems/";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    private String categoryId;

    @BeforeEach
    void createCategory() {
        var location = mvc.post()
                .uri("/api/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Category " + UUID.randomUUID() + "\"}")
                .exchange()
                .getResponse()
                .getHeader("Location");
        categoryId = location.substring(location.lastIndexOf('/') + 1);
    }

    private MvcTestResult post(String key, String name) {
        var request = mvc.post()
                .uri("/api/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"%s","price":{"amount":"10","currency":"EUR"},"categoryId":"%s"}""".formatted(name, categoryId));
        if (key != null) {
            request = request.header("Idempotency-Key", key);
        }
        return request.exchange();
    }

    private int itemsNamed(String name) {
        return jdbc.queryForObject("select count(*) from item where name = ?", Integer.class, name);
    }

    @Test
    void missingKeyIsRejected() {
        var result = post(null, "No key");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo(PROBLEMS + "invalid-request");
        assertThat(itemsNamed("No key")).isZero();
    }

    @Test
    void retryWithSameKeyAndBodyReplaysTheFirstResponse() {
        var key = UUID.randomUUID().toString();
        var name = "Retry " + key;

        var first = post(key, name);
        var second = post(key, name);

        assertThat(first).hasStatus(HttpStatus.CREATED);
        assertThat(second).hasStatus(HttpStatus.CREATED);
        assertThat(second.getResponse().getHeader("Location"))
                .isEqualTo(first.getResponse().getHeader("Location"));
        assertThat(second.getResponse().getHeader("ETag"))
                .isEqualTo(first.getResponse().getHeader("ETag"));
        assertThat(itemsNamed(name)).isEqualTo(1);
    }

    @Test
    void sameKeyWithDifferentBodyIsAConflict() {
        var key = UUID.randomUUID().toString();
        post(key, "Original " + key);

        var result = post(key, "Changed " + key);

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo(PROBLEMS + "idempotency-conflict");
        assertThat(itemsNamed("Changed " + key)).isZero();
    }

    @Test
    void failedRequestReleasesTheKey() {
        var key = UUID.randomUUID().toString();
        var invalid = mvc.post()
                .uri("/api/items")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}")
                .exchange();
        assertThat(invalid).hasStatus(HttpStatus.BAD_REQUEST);

        assertThat(post(key, "After failure " + key)).hasStatus(HttpStatus.CREATED);
    }

    @Test
    void concurrentRequestsWithTheSameKeyCreateExactlyOneItem() throws Exception {
        var key = UUID.randomUUID().toString();
        var name = "Concurrent " + key;
        var start = new CountDownLatch(1);
        var statuses = new ArrayList<Future<Integer>>();
        try (var pool = Executors.newFixedThreadPool(4)) {
            for (int i = 0; i < 4; i++) {
                statuses.add(pool.submit(() -> {
                    start.await();
                    return post(key, name).getResponse().getStatus();
                }));
            }
            start.countDown();
            for (var status : statuses) {
                assertThat(status.get()).isIn(201, 409);
            }
        }

        assertThat(itemsNamed(name)).isEqualTo(1);
    }
}
