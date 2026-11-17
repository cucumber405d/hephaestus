package de.ibikus.appa.hephaestus.servicecommunication.ordering;

import static org.assertj.core.api.Assertions.assertThat;

import de.ibikus.appa.hephaestus.servicecommunication.ordering.StubInventoryServer.Answer;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
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

/** The whole Ordering service on PostgreSQL, with Inventory replaced by a stub HTTP server. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainers.class)
class OrderingIT {

    static final StubInventoryServer INVENTORY = startStub();

    @DynamicPropertySource
    static void inventoryUrl(DynamicPropertyRegistry registry) {
        registry.add("inventory.url", () -> INVENTORY.url().toString());
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

        var placed = place();

        assertThat(placed).hasStatus(HttpStatus.CREATED);
        assertThat(mvc.get().uri(placed.getResponse().getHeader("Location")).exchange())
                .bodyJson()
                .extractingPath("$.reservationId")
                .isEqualTo(reservationId.toString());
    }

    @Test
    void inventoryOutageIs503() {
        INVENTORY.answer("/api/reservations", Answer.problem(503, "{\"status\":503}"));

        assertThat(place()).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
    }

    private org.springframework.test.web.servlet.assertj.MvcTestResult place() {
        return mvc.post()
                .uri("/api/orders")
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
