package de.ibikus.appa.hephaestus.servicecommunication.inventory.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@WebMvcTest(ReservationsController.class)
@Import({ProblemDetailsAdvice.class, ApiVersioningConfiguration.class, WebSliceFakes.class})
class ReservationsControllerTest {

    private static final String PROBLEMS = "https://hephaestus.ibikus.de/problems/";

    @Autowired
    MockMvcTester mvc;

    @Test
    void newReservationIs201WithLocation() {
        var result = reserve(UUID.randomUUID(), "HAMMER-01", 1);

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result.getResponse().getHeader("Location")).matches(".*/api/reservations/[0-9a-f-]{36}");
        assertThat(result).bodyJson().extractingPath("$.lines[0].sku").isEqualTo("HAMMER-01");
    }

    @Test
    void repeatedOrderIs200WithTheSameReservation() throws Exception {
        var orderId = UUID.randomUUID();
        var first = reserve(orderId, "HAMMER-01", 1);

        var second = reserve(orderId, "HAMMER-01", 1);

        assertThat(second).hasStatus(HttpStatus.OK);
        assertThat(second.getResponse().getContentAsString())
                .isEqualTo(first.getResponse().getContentAsString());
    }

    @Test
    void insufficientStockIs409WithSku() {
        var result = reserve(UUID.randomUUID(), "TONGS-01", 1);

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo(PROBLEMS + "insufficient-stock");
        assertThat(result).bodyJson().extractingPath("$.sku").isEqualTo("TONGS-01");
        assertThat(result).bodyJson().extractingPath("$.available").isEqualTo(0);
    }

    @Test
    void unknownSkuIs422WithSku() {
        var result = reserve(UUID.randomUUID(), "NOPE-01", 1);

        assertThat(result).hasStatus(422);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo(PROBLEMS + "unknown-sku");
        assertThat(result).bodyJson().extractingPath("$.sku").isEqualTo("NOPE-01");
    }

    @Test
    void invalidQuantityIs400() {
        var result = reserve(UUID.randomUUID(), "ANVIL-01", 0);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo(PROBLEMS + "invalid-request");
    }

    private MvcTestResult reserve(UUID orderId, String sku, int quantity) {
        return mvc.post()
                .uri("/api/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"orderId":"%s","lines":[{"sku":"%s","quantity":%d}]}""".formatted(orderId, sku, quantity))
                .exchange();
    }
}
