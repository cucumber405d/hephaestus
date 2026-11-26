package de.ibikus.appa.hephaestus.es.forge.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.ibikus.appa.hephaestus.es.forge.Containers;
import de.ibikus.appa.hephaestus.es.forge.FakeSupplier;
import de.ibikus.appa.hephaestus.es.forge.Journal;
import de.ibikus.appa.hephaestus.es.forge.domain.workorder.WorkOrderDecider;
import de.ibikus.appa.hephaestus.es.forge.application.WorkOrders;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** The smith API on the journal: FW-<order> ids, start by a named smith, 409 on refusals, 404 on unknown ids. */
@SpringBootTest(properties = {"supplier.timeout=500ms", "forge.supply-retry=300ms"})
@AutoConfigureMockMvc
@Import({Containers.class, Journal.class})
class SmithApiIT {

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

    @AfterEach
    void fastSupplier() {
        FakeSupplier.delayMs = 0;
    }

    @Test
    void repeatedTransitionIsConflict() {
        var wo = requested("IRON-01", 1);
        assertThat(wo).matches("FW-[0-9a-f-]{36}");
        assertThat(mvc.post().uri("/api/work-orders/{id}/start?smith=anna", wo)).hasStatusOk();
        assertThat(mvc.post().uri("/api/work-orders/{id}/complete", wo)).hasStatusOk();
        assertThat(mvc.post().uri("/api/work-orders/{id}/complete", wo)).hasStatus(409);
        assertThat(mvc.post().uri("/api/work-orders/{id}/start?smith=anna", wo)).hasStatus(409);
    }

    @Test
    void startUnknownIs404() {
        assertThat(mvc.post().uri("/api/work-orders/{id}/start?smith=anna", "FW-missing")).hasStatus(404);
    }

    @Test
    void completeUnknownIs404() {
        assertThat(mvc.post().uri("/api/work-orders/{id}/complete", "FW-missing")).hasStatus(404);
    }

    @Test
    void startWithoutSmithIs400() {
        var wo = requested("IRON-01", 1);
        assertThat(mvc.post().uri("/api/work-orders/{id}/start", wo)).hasStatus(400);
    }

    @Test
    void theListIsTheWorkshopBoard() {
        var wo = requested("IRON-01", 1);
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(mvc.get().uri("/api/work-orders?status=ALLOCATED"))
                        .bodyJson()
                        .extractingPath("$[?(@.id == '" + wo + "')].material")
                        .asArray()
                        .containsExactly("IRON-01"));
    }

    @Test
    void undeliverableMaterialFailsTheWorkOrder() {
        var orderId = UUID.randomUUID();
        workOrders.request(orderId, "MITHRIL-01", 1, Optional.empty());
        assertThat(journal.status(orderId)).isEqualTo("FAILED");
        assertThat(journal.workOrder(orderId).failureReason()).isEqualTo("material not deliverable: MITHRIL-01");
    }

    @Test
    void aRepeatedRequestCreatesOneWorkOrder() {
        var orderId = UUID.randomUUID();
        workOrders.request(orderId, "IRON-01", 1, Optional.empty());
        workOrders.request(orderId, "IRON-01", 1, Optional.empty());
        assertThat(journal.requests(orderId)).isEqualTo(1);
    }

    @Test
    void aSlowSupplierLeavesTheWorkOrderWaitingUntilTheRetryGetsTheMaterial() {
        FakeSupplier.delayMs = 2_000;
        var orderId = UUID.randomUUID();
        workOrders.request(orderId, "STEEL-01", 500, Optional.empty());
        var wo = WorkOrderDecider.idFor(orderId);
        assertThat(journal.status(orderId)).isEqualTo("WAITING_FOR_MATERIAL");
        assertThat(mvc.post().uri("/api/work-orders/{id}/start?smith=anna", wo)).hasStatus(409);

        FakeSupplier.delayMs = 0;
        await().atMost(Duration.ofSeconds(10)).until(() -> journal.status(orderId).equals("ALLOCATED"));
        assertThat(journal.events("material-STEEL-01", "StockReceived")).isPositive();
        assertThat(mvc.post().uri("/api/work-orders/{id}/start?smith=anna", wo)).hasStatusOk();
    }

    private String requested(String material, int quantity) {
        var orderId = UUID.randomUUID();
        workOrders.request(orderId, material, quantity, Optional.empty());
        return WorkOrderDecider.idFor(orderId);
    }
}
