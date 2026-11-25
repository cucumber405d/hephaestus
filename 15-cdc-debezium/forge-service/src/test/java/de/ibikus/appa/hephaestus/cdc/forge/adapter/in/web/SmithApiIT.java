package de.ibikus.appa.hephaestus.cdc.forge.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.ibikus.appa.hephaestus.cdc.forge.Containers;
import de.ibikus.appa.hephaestus.cdc.forge.FakeSupplier;
import de.ibikus.appa.hephaestus.cdc.forge.application.WorkOrders;
import java.time.Duration;
import java.util.Optional;
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

/** The smith API as in 13: FW- ids, 409 on a repeated transition, waiting material. */
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
        assertThat(wo).matches("FW-[0-9a-f-]{36}");
        assertThat(mvc.post().uri("/api/work-orders/{id}/start", wo)).hasStatusOk();
        assertThat(mvc.post().uri("/api/work-orders/{id}/complete", wo)).hasStatusOk();
        assertThat(mvc.post().uri("/api/work-orders/{id}/complete", wo)).hasStatus(409);
        assertThat(mvc.post().uri("/api/work-orders/{id}/start", wo)).hasStatus(409);
        assertThat(mvc.post().uri("/api/work-orders/{id}/start", "FW-missing")).hasStatus(404);
    }

    @Test
    void theListShowsTheServicesOrdersWithTheirOrigin() {
        var wo = requested("IRON-01", 1);
        assertThat(mvc.get().uri("/api/work-orders"))
                .bodyJson()
                .extractingPath("$[?(@.id == '" + wo + "')].origin")
                .asArray()
                .containsExactly("SERVICE");
    }

    @Test
    void undeliverableMaterialFailsTheWorkOrder() {
        var wo = requested("MITHRIL-01", 1);
        assertThat(jdbc.sql("select status from work_order where id = ?")
                        .param(wo)
                        .query(String.class)
                        .single())
                .isEqualTo("FAILED");
    }

    @Test
    void aRepeatedRequestCreatesOneWorkOrder() {
        var orderId = UUID.randomUUID();
        workOrders.request(orderId, "IRON-01", 1, Optional.empty());
        workOrders.request(orderId, "IRON-01", 1, Optional.empty());
        assertThat(jdbc.sql("select count(*) from work_order where order_id = ?")
                        .param(orderId)
                        .query(Integer.class)
                        .single())
                .isEqualTo(1);
    }

    @Test
    void aSlowSupplierLeavesTheWorkOrderWaitingUntilTheRetryGetsTheMaterial() {
        FakeSupplier.delayMs = 2_000;
        var wo = requested("STEEL-01", 500);
        assertThat(mvc.post().uri("/api/work-orders/{id}/start", wo)).hasStatus(409);

        FakeSupplier.delayMs = 0;
        await().atMost(Duration.ofSeconds(10))
                .until(() -> jdbc.sql("select material_ready from work_order where id = ?")
                        .param(wo)
                        .query(Boolean.class)
                        .single());
        assertThat(mvc.post().uri("/api/work-orders/{id}/start", wo)).hasStatusOk();
    }

    private String requested(String material, int quantity) {
        var orderId = UUID.randomUUID();
        workOrders.request(orderId, material, quantity, Optional.empty());
        return jdbc.sql("select id from work_order where order_id = ?")
                .param(orderId)
                .query(String.class)
                .single();
    }
}
