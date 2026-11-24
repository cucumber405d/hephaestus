package de.ibikus.appa.hephaestus.monolith.forge.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import de.ibikus.appa.hephaestus.monolith.forge.Containers;
import de.ibikus.appa.hephaestus.monolith.forge.FakeMonolith;
import de.ibikus.appa.hephaestus.monolith.forge.FakeSupplier;
import de.ibikus.appa.hephaestus.monolith.forge.application.WorkOrders;
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

/** The Strangler Fig facade: the service's list wraps the monolith's open work orders; a silent monolith is partial. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(Containers.class)
class FacadeListIT {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    WorkOrders workOrders;

    @DynamicPropertySource
    static void outside(DynamicPropertyRegistry registry) {
        registry.add("supplier.url", FakeSupplier::url);
        registry.add("monolith.url", FakeMonolith::url);
    }

    @AfterEach
    void monolithBack() {
        FakeMonolith.up = true;
    }

    @Test
    void theListHoldsBothSidesWithTheirOrigin() {
        workOrders.request(UUID.randomUUID(), "IRON-01", 1);
        var result = mvc.get().uri("/api/work-orders").exchange();
        assertThat(result).hasStatusOk().doesNotContainHeader("X-Partial");
        assertThat(result)
                .bodyJson()
                .extractingPath("$[?(@.id == 'WO-000042')].origin")
                .asArray()
                .containsExactly("MONOLITH");
        assertThat(result)
                .bodyJson()
                .extractingPath("$[?(@.origin == 'SERVICE')].id")
                .asArray()
                .isNotEmpty();
    }

    @Test
    void aSilentMonolithGivesThePartialList() {
        FakeMonolith.up = false;
        workOrders.request(UUID.randomUUID(), "IRON-01", 1);
        var result = mvc.get().uri("/api/work-orders").exchange();
        assertThat(result).hasStatusOk().hasHeader("X-Partial", "monolith-unavailable");
        assertThat(result)
                .bodyJson()
                .extractingPath("$[?(@.origin == 'MONOLITH')]")
                .asArray()
                .isEmpty();
    }

    @Test
    void theStatusFilterAppliesToBothSides() {
        assertThat(mvc.get().uri("/api/work-orders?status=PLANNED"))
                .bodyJson()
                .extractingPath("$[?(@.origin == 'MONOLITH')]")
                .asArray()
                .isEmpty();
    }
}
