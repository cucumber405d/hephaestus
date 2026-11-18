package de.ibikus.appa.hephaestus.resilience.supplier;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest({OffersController.class, AdminController.class})
@Import(SupplierState.class)
class SupplierControllersTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    SupplierState state;

    @BeforeEach
    void healthy() {
        state.faults(Faults.NONE);
        state.resetCalls();
    }

    @Test
    void knownSkuHasItsOffer() {
        var result = mvc.get().uri("/api/offers/ANVIL-01").exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.price").isEqualTo(120.00);
        assertThat(result).bodyJson().extractingPath("$.currency").isEqualTo("EUR");
        assertThat(result).bodyJson().extractingPath("$.leadTimeDays").isEqualTo(5);
        assertThat(result).bodyJson().extractingPath("$.maxQuantity").isEqualTo(20);
    }

    @Test
    void unknownSkuIs404() {
        assertThat(mvc.get().uri("/api/offers/NOPE-01").exchange()).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void injectedErrorsAreAnsweredAndStillCounted() {
        setFaults("{\"errorRate\":1.0,\"status\":503,\"delayMs\":0}");

        assertThat(mvc.get().uri("/api/offers/ANVIL-01").exchange()).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(state.offerCalls()).isEqualTo(1);
    }

    @Test
    void injectedDelayHoldsTheAnswer() {
        setFaults("{\"errorRate\":0.0,\"status\":503,\"delayMs\":300}");
        var started = System.nanoTime();

        assertThat(mvc.get().uri("/api/offers/ANVIL-01").exchange()).hasStatus(HttpStatus.OK);
        assertThat((System.nanoTime() - started) / 1_000_000).isGreaterThanOrEqualTo(300);
    }

    @Test
    void clearingFaultsHealsTheSupplier() {
        setFaults("{\"errorRate\":1.0,\"status\":500,\"delayMs\":0}");

        assertThat(mvc.delete().uri("/admin/faults").exchange()).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.get().uri("/api/offers/ANVIL-01").exchange()).hasStatus(HttpStatus.OK);
    }

    @Test
    void invalidFaultsAreRejected() {
        var result = mvc.put()
                .uri("/admin/faults")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"errorRate\":2.0,\"status\":503,\"delayMs\":0}")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void statsCountEveryOfferCall() {
        mvc.get().uri("/api/offers/ANVIL-01").exchange();
        mvc.get().uri("/api/offers/NOPE-01").exchange();

        assertThat(mvc.get().uri("/admin/stats").exchange()).bodyJson().extractingPath("$.offerCalls").isEqualTo(2);
    }

    private void setFaults(String json) {
        assertThat(mvc.put()
                        .uri("/admin/faults")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .exchange())
                .hasStatus(HttpStatus.OK);
    }
}
