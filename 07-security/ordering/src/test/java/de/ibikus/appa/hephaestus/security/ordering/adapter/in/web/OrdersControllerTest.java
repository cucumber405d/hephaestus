package de.ibikus.appa.hephaestus.security.ordering.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import de.ibikus.appa.hephaestus.security.ordering.TestTokens;
import de.ibikus.appa.hephaestus.security.ordering.application.fakes.FakeInventory;
import de.ibikus.appa.hephaestus.security.ordering.application.fakes.InMemoryOrderRepository;
import de.ibikus.appa.hephaestus.security.ordering.application.service.OrderService;
import de.ibikus.appa.hephaestus.security.ordering.domain.InsufficientStockException;
import de.ibikus.appa.hephaestus.security.ordering.domain.InventoryUnavailableException;
import de.ibikus.appa.hephaestus.security.ordering.domain.UnknownItemException;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(OrdersController.class)
@Import({ProblemDetailsAdvice.class, SecurityConfiguration.class, OrdersControllerTest.Fakes.class})
class OrdersControllerTest {

    private static final String PROBLEMS = "https://hephaestus.ibikus.de/problems/";

    @TestConfiguration(proxyBeanMethods = false)
    static class Fakes {

        @Bean
        FakeInventory fakeInventory() {
            return new FakeInventory();
        }

        @Bean
        OrderService orderService(FakeInventory inventory) {
            return new OrderService(inventory, new InMemoryOrderRepository(), Clock.systemUTC());
        }
    }

    /** Never called: jwt() puts the authentication in place; the resource server just needs a decoder bean. */
    @MockitoBean
    JwtDecoder jwtDecoder;

    @Autowired
    MockMvcTester mvc;

    @Autowired
    FakeInventory inventory;

    @BeforeEach
    void inventoryWorks() {
        inventory.succeed();
    }

    @Test
    void placedOrderIs201AndCanBeReadBack() {
        var placed = place("ANVIL-01", 2);

        assertThat(placed).hasStatus(HttpStatus.CREATED);
        assertThat(placed).bodyJson().extractingPath("$.status").isEqualTo("CONFIRMED");
        var location = placed.getResponse().getHeader("Location");
        assertThat(location).matches(".*/api/orders/[0-9a-f-]{36}");
        assertThat(mvc.get().uri(location).with(TestTokens.alice()).exchange()).hasStatus(HttpStatus.OK);
    }

    @Test
    void anonymousIs401() {
        var result = mvc.post()
                .uri("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("ANVIL-01", 1))
                .exchange();

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.get().uri("/api/orders/{id}", UUID.randomUUID()).exchange())
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void smithMayNotPlaceOrders() {
        assertThat(place(TestTokens.bob(), "ANVIL-01", 1)).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(inventory.reservations()).isEmpty();
    }

    @Test
    void adminMayNotPlaceOrdersEither() {
        assertThat(place(TestTokens.admin(), "ANVIL-01", 1)).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void anotherCustomersOrderIs404() {
        var location = place("ANVIL-01", 1).getResponse().getHeader("Location");

        var result = mvc.get().uri(location).with(TestTokens.carol()).exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.type")
                .isEqualTo(PROBLEMS + "not-found");
    }

    @Test
    void adminReadsAnyOrder() {
        var location = place("ANVIL-01", 1).getResponse().getHeader("Location");

        assertThat(mvc.get().uri(location).with(TestTokens.admin()).exchange()).hasStatus(HttpStatus.OK);
    }

    @Test
    void insufficientStockIs409WithSku() {
        inventory.failWith(new InsufficientStockException("TONGS-01"));

        var result = place("TONGS-01", 1);

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo(PROBLEMS + "insufficient-stock");
        assertThat(result).bodyJson().extractingPath("$.sku").isEqualTo("TONGS-01");
    }

    @Test
    void unknownItemIs422() {
        inventory.failWith(new UnknownItemException("NOPE-01"));

        var result = place("NOPE-01", 1);

        assertThat(result).hasStatus(422);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo(PROBLEMS + "unknown-item");
    }

    @Test
    void unavailableInventoryIs503WithRetryAfter() throws Exception {
        inventory.failWith(new InventoryUnavailableException("down", new RuntimeException("connection refused")));

        var result = place("ANVIL-01", 1);

        assertThat(result).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(result.getResponse().getHeader("Retry-After")).isEqualTo("5");
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo(PROBLEMS + "inventory-unavailable");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("connection refused");
    }

    @Test
    void invalidQuantityIs400() {
        assertThat(place("ANVIL-01", 0)).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void missingOrderIs404() {
        assertThat(mvc.get()
                        .uri("/api/orders/{id}", UUID.randomUUID())
                        .with(TestTokens.alice())
                        .exchange())
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void availabilityAsksInventory() {
        var result = mvc.get()
                .uri("/api/orders/availability/ANVIL-01")
                .with(TestTokens.bob())
                .exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.available").isEqualTo(7);
    }

    private MvcTestResult place(String sku, int quantity) {
        return place(TestTokens.alice(), sku, quantity);
    }

    private MvcTestResult place(RequestPostProcessor caller, String sku, int quantity) {
        return mvc.post()
                .uri("/api/orders")
                .with(caller)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(sku, quantity))
                .exchange();
    }

    private static String body(String sku, int quantity) {
        return """
                {"lines":[{"sku":"%s","quantity":%d}]}""".formatted(sku, quantity);
    }
}
