package de.ibikus.appa.hephaestus.restservice.catalog.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import de.ibikus.appa.hephaestus.restservice.catalog.PostgresTestcontainers;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** Two clients PUT with the same If-Match at the same moment: exactly one wins, the other gets 412 (no lost update). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainers.class)
class ConcurrentUpdateIT {

    @Autowired
    MockMvcTester mvc;

    private String location(String uri, String body) {
        return mvc.post()
                .uri(uri)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange()
                .getResponse()
                .getHeader("Location");
    }

    @Test
    void concurrentPutsWithTheSameVersionLetExactlyOneWin() throws Exception {
        var category = location("/api/categories", "{\"name\":\"Race " + UUID.randomUUID() + "\"}");
        var categoryId = category.substring(category.lastIndexOf('/') + 1);
        var item = location("/api/items", """
                {"name":"Contested","price":{"amount":"10","currency":"EUR"},"categoryId":"%s"}""".formatted(categoryId));
        var start = new CountDownLatch(1);
        var statuses = new ArrayList<Future<Integer>>();

        try (var pool = Executors.newFixedThreadPool(2)) {
            for (int i = 0; i < 2; i++) {
                var name = "Writer " + i;
                statuses.add(pool.submit(() -> {
                    start.await();
                    return mvc.put()
                            .uri(item)
                            .header("If-Match", "\"0\"")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"%s","price":{"amount":"11","currency":"EUR"},"categoryId":"%s"}""".formatted(name, categoryId))
                            .exchange()
                            .getResponse()
                            .getStatus();
                }));
            }
            start.countDown();
            var results = new ArrayList<Integer>();
            for (var status : statuses) {
                results.add(status.get());
            }
            assertThat(results).containsExactlyInAnyOrder(200, 412);
        }

        assertThat(mvc.get().uri(item)).hasHeader("ETag", "\"1\"");
    }
}
