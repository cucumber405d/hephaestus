package de.ibikus.appa.hephaestus.monolith.modular.forge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.ibikus.appa.hephaestus.monolith.modular.Containers;
import de.ibikus.appa.hephaestus.monolith.modular.FakeSupplier;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** The smith's API: a repeated transition is 409; work cannot start before the material is there. */
@SpringBootTest(properties = {"supplier.timeout=500ms", "forge.supply-retry=300ms"})
@AutoConfigureMockMvc
@Import(Containers.class)
class SmithApiIT {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    WorkOrders workOrders;

    @Autowired
    JdbcClient jdbc;

    @DynamicPropertySource
    static void supplier(DynamicPropertyRegistry registry) {
        registry.add("supplier.url", FakeSupplier::url);
    }

    @AfterEach
    void fastSupplier() {
        FakeSupplier.delayMs = 0;
    }

    @Test
    void repeatedTransitionIsConflict() {
        var wo = requested("IRON-01", 1);
        assertThat(mvc.post().uri("/api/work-orders/{id}/start", wo)).hasStatusOk();
        assertThat(mvc.post().uri("/api/work-orders/{id}/complete", wo)).hasStatusOk();
        assertThat(mvc.post().uri("/api/work-orders/{id}/complete", wo)).hasStatus(409);
        assertThat(mvc.post().uri("/api/work-orders/{id}/start", wo)).hasStatus(409);
        assertThat(mvc.post().uri("/api/work-orders/{id}/start", "WO-999999")).hasStatus(404);
    }

    @Test
    void aSlowSupplierLeavesTheWorkOrderWaitingUntilTheRetryGetsTheMaterial() {
        FakeSupplier.delayMs = 2_000;
        // more than the stock: the supplier is asked and times out
        var wo = requested("STEEL-01", 500);
        assertThat(mvc.post().uri("/api/work-orders/{id}/start", wo)).hasStatus(409);

        FakeSupplier.delayMs = 0;
        await().atMost(Duration.ofSeconds(10))
                .until(() -> jdbc.sql("select material_ready from forge.work_order where id = ?")
                        .param(wo)
                        .query(Boolean.class)
                        .single());
        assertThat(mvc.post().uri("/api/work-orders/{id}/start", wo)).hasStatusOk();
    }

    private String requested(String material, int quantity) {
        var orderId = UUID.randomUUID();
        workOrders.request(orderId, material, quantity);
        return jdbc.sql("select id from forge.work_order where order_id = ?")
                .param(orderId)
                .query(String.class)
                .single();
    }
}
