package de.ibikus.appa.hephaestus.security.ordering;

import static org.assertj.core.api.Assertions.assertThat;

import de.ibikus.appa.hephaestus.security.ordering.StubInventoryServer.Answer;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** The whole Ordering service on PostgreSQL, with Inventory replaced by a stub HTTP server. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainers.class)
class OrderingIT {

    static final StubInventoryServer INVENTORY = startStub();

    /** The stub also plays Keycloak's token endpoint (issuer and key set: test defaults in config/application.properties). */
    @DynamicPropertySource
    static void inventoryAndKeycloak(DynamicPropertyRegistry registry) {
        registry.add("inventory.url", () -> INVENTORY.url().toString());
        registry.add("OAUTH2_TOKEN_URI", () -> INVENTORY.url() + "/token");
    }

    @BeforeAll
    static void keycloakIssuesServiceTokens() {
        INVENTORY.answer("/token", Answer.token("it-service-token"));
    }

    @AfterAll
    static void stopStub() {
        INVENTORY.close();
    }

    @Autowired
    MockMvcTester mvc;

    @Test
    void orderIsStoredWithTheReservationFromInventory() {
        var reservationId = UUID.randomUUID();
        INVENTORY.answer("/api/reservations", Answer.json(201, """
                {"id":"%s","orderId":"%s"}""".formatted(reservationId, UUID.randomUUID())));

        var placed = place(TestTokens.alice());

        assertThat(placed).hasStatus(HttpStatus.CREATED);
        assertThat(INVENTORY.lastRequest().authorization()).isEqualTo("Bearer it-service-token");
        var location = placed.getResponse().getHeader("Location");
        assertThat(mvc.get().uri(location).with(TestTokens.alice()).exchange())
                .bodyJson()
                .extractingPath("$.reservationId")
                .isEqualTo(reservationId.toString());
        // The owner is stored with the order: another customer gets 404 after a round trip through PostgreSQL
        assertThat(mvc.get().uri(location).with(TestTokens.carol()).exchange()).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void inventoryOutageIs503() {
        INVENTORY.answer("/api/reservations", Answer.problem(503, "{\"status\":503}"));

        assertThat(place(TestTokens.alice())).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
    }

    private org.springframework.test.web.servlet.assertj.MvcTestResult place(RequestPostProcessor caller) {
        return mvc.post()
                .uri("/api/orders")
                .with(caller)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"lines\":[{\"sku\":\"ANVIL-01\",\"quantity\":1}]}")
                .exchange();
    }

    private static StubInventoryServer startStub() {
        try {
            return new StubInventoryServer();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
