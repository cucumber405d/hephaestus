package de.ibikus.appa.hephaestus.security.inventory.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(ReservationsController.class)
@Import({ProblemDetailsAdvice.class, ApiVersioningConfiguration.class, SecurityConfiguration.class, WebSliceFakes.class
})
class ReservationsControllerTest {

    private static final String PROBLEMS = "https://hephaestus.ibikus.de/problems/";

    /** Never called: jwt() puts the authentication in place; the resource server just needs a decoder bean. */
    @MockitoBean
    JwtDecoder jwtDecoder;

    @Autowired
    MockMvcTester mvc;

    @Test
    void reservationWithoutTokenIs401() {
        var result = mvc.post()
                .uri("/api/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(UUID.randomUUID(), "HAMMER-01", 1))
                .exchange();

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(result.getResponse().getHeader("WWW-Authenticate")).startsWith("Bearer");
    }

    @Test
    void customerTokenCannotReserveDirectly() {
        assertThat(reserve(TestTokens.customer(), UUID.randomUUID(), "HAMMER-01", 1))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminTokenCannotReserveEither() {
        assertThat(reserve(TestTokens.withRoles("admin", "admin"), UUID.randomUUID(), "HAMMER-01", 1))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

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
        return reserve(TestTokens.orderingService(), orderId, sku, quantity);
    }

    private MvcTestResult reserve(RequestPostProcessor caller, UUID orderId, String sku, int quantity) {
        return mvc.post()
                .uri("/api/reservations")
                .with(caller)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(orderId, sku, quantity))
                .exchange();
    }

    private static String body(UUID orderId, String sku, int quantity) {
        return """
                {"orderId":"%s","lines":[{"sku":"%s","quantity":%d}]}""".formatted(orderId, sku, quantity);
    }
}
