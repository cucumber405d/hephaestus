package de.ibikus.appa.hephaestus.monolith.legacy;

import static org.assertj.core.api.Assertions.assertThat;

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

/** The legacy works — that is why nobody dared to touch it. The same cases run against modular/ (Task 3). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(Containers.class)
class LegacyFlowIT {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @DynamicPropertySource
    static void supplier(DynamicPropertyRegistry registry) {
        registry.add("SUPPLIER_URL", FakeSupplier::url);
    }

    @Test
    void stockOrderIsReadyAtOnce() {
        var id = place("{\"customerId\":\"c1\",\"sku\":\"HAMMER-01\"}");
        assertThat(mvc.get().uri("/api/orders/{id}", id))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("READY");
    }

    @Test
    void customOrderBecomesReadyWhenTheSmithCompletesTheWorkOrder() {
        var id = place("{\"customerId\":\"c1\",\"custom\":true,\"material\":\"IRON-01\",\"quantity\":2}");
        var wo = workOrderOf(id);
        assertThat(wo).matches("WO-\\d{6}");
        assertThat(status(id)).isEqualTo("ACCEPTED");

        assertThat(mvc.post().uri("/api/work-orders/{id}/start", wo)).hasStatusOk();
        assertThat(mvc.post().uri("/api/work-orders/{id}/complete", wo)).hasStatusOk();

        assertThat(status(id)).isEqualTo("READY");
    }

    @Test
    void shortMaterialIsOrderedFromTheSupplier() {
        // 25 > the 20 seeded: the shortfall comes from the supplier, the work order is still planned
        var id = place("{\"customerId\":\"c1\",\"custom\":true,\"material\":\"STEEL-01\",\"quantity\":25}");
        assertThat(jdbc.sql("select status from work_order where order_id = ?")
                        .param(java.util.UUID.fromString(id))
                        .query(String.class)
                        .single())
                .isEqualTo("PLANNED");
    }

    @Test
    void undeliverableMaterialFailsTheWorkOrderAndRejectsTheOrder() {
        var id = place("{\"customerId\":\"c1\",\"custom\":true,\"material\":\"MITHRIL-01\",\"quantity\":1}");
        assertThat(jdbc.sql("select status from work_order where order_id = ?")
                        .param(java.util.UUID.fromString(id))
                        .query(String.class)
                        .single())
                .isEqualTo("FAILED");
        assertThat(mvc.get().uri("/api/orders/{id}", id))
                .bodyJson()
                .extractingPath("$.reason")
                .isEqualTo("material not deliverable: MITHRIL-01");
    }

    @Test
    void repeatedTransitionIsConflict() {
        var id = place("{\"customerId\":\"c1\",\"custom\":true,\"material\":\"IRON-01\",\"quantity\":1}");
        var wo = workOrderOf(id);
        assertThat(mvc.post().uri("/api/work-orders/{id}/start", wo)).hasStatusOk();
        assertThat(mvc.post().uri("/api/work-orders/{id}/complete", wo)).hasStatusOk();
        assertThat(mvc.post().uri("/api/work-orders/{id}/complete", wo)).hasStatus(409);
        assertThat(mvc.post().uri("/api/work-orders/{id}/start", wo)).hasStatus(409);
    }

    private String place(String json) {
        var result = mvc.post()
                .uri("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .exchange();
        assertThat(result).hasStatus(202);
        return jdbc.sql("select id::text from customer_order order by created_at desc limit 1")
                .query(String.class)
                .single();
    }

    private String status(String orderId) {
        return jdbc.sql("select status from customer_order where id = ?")
                .param(java.util.UUID.fromString(orderId))
                .query(String.class)
                .single();
    }

    private String workOrderOf(String orderId) {
        return jdbc.sql("select id from work_order where order_id = ?")
                .param(java.util.UUID.fromString(orderId))
                .query(String.class)
                .single();
    }
}
