package de.ibikus.appa.hephaestus.monolith.modular;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** The cases of LegacyFlowIT, against the modular monolith: same behaviour, now eventually (events after commit). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(Containers.class)
class ModularFlowIT {

    static final Duration WAIT = Duration.ofSeconds(10);

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @DynamicPropertySource
    static void supplier(DynamicPropertyRegistry registry) {
        registry.add("supplier.url", FakeSupplier::url);
    }

    @Test
    void stockOrderIsReadyAtOnce() {
        var id = place("{\"customerId\":\"c1\",\"sku\":\"HAMMER-01\"}");
        assertThat(status(id)).isEqualTo("READY");
    }

    @Test
    void customOrderBecomesReadyWhenTheSmithCompletesTheWorkOrder() {
        var id = place("{\"customerId\":\"c1\",\"custom\":true,\"material\":\"IRON-01\",\"quantity\":2}");
        var wo = await().atMost(WAIT).until(() -> workOrderOf(id), w -> w != null && ready(w));
        assertThat(wo).matches("WO-\\d{6}");
        assertThat(status(id)).isEqualTo("ACCEPTED");

        assertThat(mvc.post().uri("/api/work-orders/{id}/start", wo)).hasStatusOk();
        assertThat(mvc.post().uri("/api/work-orders/{id}/complete", wo)).hasStatusOk();

        await().atMost(WAIT).until(() -> status(id), "READY"::equals);
    }

    @Test
    void shortMaterialIsOrderedFromTheSupplier() {
        var id = place("{\"customerId\":\"c1\",\"custom\":true,\"material\":\"STEEL-01\",\"quantity\":25}");
        var wo = await().atMost(WAIT).until(() -> workOrderOf(id), w -> w != null && ready(w));
        assertThat(jdbc.sql("select status from forge.work_order where id = ?")
                        .param(wo)
                        .query(String.class)
                        .single())
                .isEqualTo("PLANNED");
    }

    @Test
    void undeliverableMaterialFailsTheWorkOrderAndRejectsTheOrder() {
        var id = place("{\"customerId\":\"c1\",\"custom\":true,\"material\":\"MITHRIL-01\",\"quantity\":1}");
        await().atMost(WAIT).until(() -> status(id), "REJECTED"::equals);
        assertThat(jdbc.sql("select status from forge.work_order where order_id = ?")
                        .param(UUID.fromString(id))
                        .query(String.class)
                        .single())
                .isEqualTo("FAILED");
        assertThat(mvc.get().uri("/api/orders/{id}", id))
                .bodyJson()
                .extractingPath("$.reason")
                .isEqualTo("material not deliverable: MITHRIL-01");
    }

    private String place(String json) {
        var result = mvc.post()
                .uri("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .exchange();
        assertThat(result).hasStatus(202);
        return jdbc.sql("select id::text from ordering.customer_order order by created_at desc limit 1")
                .query(String.class)
                .single();
    }

    private String status(String orderId) {
        return jdbc.sql("select status from ordering.customer_order where id = ?")
                .param(UUID.fromString(orderId))
                .query(String.class)
                .single();
    }

    private String workOrderOf(String orderId) {
        return jdbc.sql("select id from forge.work_order where order_id = ?")
                .param(UUID.fromString(orderId))
                .query(String.class)
                .optional()
                .orElse(null);
    }

    private boolean ready(String workOrderId) {
        return jdbc.sql("select material_ready from forge.work_order where id = ?")
                .param(workOrderId)
                .query(Boolean.class)
                .single();
    }
}
