package de.ibikus.appa.hephaestus.es.forge.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import de.ibikus.appa.hephaestus.es.forge.Containers;
import de.ibikus.appa.hephaestus.es.forge.FakeSupplier;
import de.ibikus.appa.hephaestus.es.forge.Journal;
import de.ibikus.appa.hephaestus.es.forge.application.WorkOrders;
import de.ibikus.appa.hephaestus.es.forge.domain.workorder.WorkOrderDecider;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** Ten smiths take the same work order at once: the stream version lets exactly one of them win. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({Containers.class, Journal.class})
class ConcurrentStartIT {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    WorkOrders workOrders;

    @Autowired
    Journal journal;

    @DynamicPropertySource
    static void supplier(DynamicPropertyRegistry registry) {
        registry.add("supplier.url", FakeSupplier::url);
    }

    @Test
    void parallelStartsOneWins() throws Exception {
        var orderId = UUID.randomUUID();
        workOrders.request(orderId, "IRON-01", 1, Optional.empty());
        var id = WorkOrderDecider.idFor(orderId);
        var go = new CountDownLatch(1);
        var statuses = new ArrayList<Future<Integer>>();
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 10; i++) {
                var smith = "smith-" + i;
                statuses.add(pool.submit(() -> {
                    go.await();
                    return mvc.post()
                            .uri("/api/work-orders/{id}/start?smith={smith}", id, smith)
                            .exchange()
                            .getResponse()
                            .getStatus();
                }));
            }
            go.countDown();
        }
        var codes = statuses.stream()
                .map(f -> {
                    try {
                        return f.get();
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                })
                .toList();

        assertThat(codes).filteredOn(c -> c == 200).hasSize(1);
        assertThat(codes).filteredOn(c -> c == 409).hasSize(9);
        assertThat(journal.events(id, "WorkOrderStarted")).isEqualTo(1);
    }
}
