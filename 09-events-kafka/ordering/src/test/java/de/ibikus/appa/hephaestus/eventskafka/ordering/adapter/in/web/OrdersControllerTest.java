package de.ibikus.appa.hephaestus.eventskafka.ordering.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import de.ibikus.appa.hephaestus.eventskafka.ordering.application.fakes.FakeInventory;
import de.ibikus.appa.hephaestus.eventskafka.ordering.application.fakes.FakeOrderEvents;
import de.ibikus.appa.hephaestus.eventskafka.ordering.application.fakes.InMemoryOrderRepository;
import de.ibikus.appa.hephaestus.eventskafka.ordering.application.service.OrderService;
import de.ibikus.appa.hephaestus.eventskafka.ordering.domain.InsufficientStockException;
import de.ibikus.appa.hephaestus.eventskafka.ordering.domain.InventoryUnavailableException;
import de.ibikus.appa.hephaestus.eventskafka.ordering.domain.UnknownItemException;
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
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@WebMvcTest({OrdersController.class, AdminController.class})
@Import({ProblemDetailsAdvice.class, OrdersControllerTest.Fakes.class})
class OrdersControllerTest {

    private static final String PROBLEMS = "https://hephaestus.ibikus.de/problems/";

    @TestConfiguration(proxyBeanMethods = false)
    static class Fakes {

        @Bean
        FakeInventory fakeInventory() {
            return new FakeInventory();
        }

        @Bean
        FakeOrderEvents fakeOrderEvents() {
            return new FakeOrderEvents();
        }

        @Bean
        OrderService orderService(FakeInventory inventory, FakeOrderEvents events) {
            return new OrderService(inventory, new InMemoryOrderRepository(), events, Clock.systemUTC());
        }
    }

    @Autowired
    MockMvcTester mvc;

    @Autowired
    FakeInventory inventory;

    @Autowired
    FakeOrderEvents events;

    @BeforeEach
    void inventoryWorks() {
        inventory.succeed();
        events.brokerUp();
    }

    @Test
    void placedOrderIs201AndCanBeReadBack() {
        var placed = place("ANVIL-01", 2);

        assertThat(placed).hasStatus(HttpStatus.CREATED);
        assertThat(placed).bodyJson().extractingPath("$.status").isEqualTo("CONFIRMED");
        var location = placed.getResponse().getHeader("Location");
        assertThat(location).matches(".*/api/orders/[0-9a-f-]{36}");
        assertThat(mvc.get().uri(location).exchange()).hasStatus(HttpStatus.OK);
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
        assertThat(mvc.get().uri("/api/orders/{id}", UUID.randomUUID()).exchange())
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void availabilityAsksInventory() {
        var result = mvc.get().uri("/api/orders/availability/ANVIL-01").exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.available").isEqualTo(7);
    }

    @Test
    void brokerDownIs503EventPublishFailed() {
        events.brokerDown();

        var result = place("ANVIL-01", 1);

        assertThat(result).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo(PROBLEMS + "event-publish-failed");
    }

    @Test
    void missingCustomerIs400() {
        var result = mvc.post()
                .uri("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"lines\":[{\"sku\":\"ANVIL-01\",\"quantity\":1}]}")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void republishAnswers202WithTheSameEventId() {
        var placed = place("ANVIL-01", 1);
        var orderId = placed.getResponse().getHeader("Location").replaceAll(".*/", "");

        var result = mvc.post().uri("/admin/republish/{id}", orderId).exchange();

        assertThat(result).hasStatus(HttpStatus.ACCEPTED);
        assertThat(events.published()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(events.published().getLast().eventId())
                .isEqualTo(events.published().get(events.published().size() - 2).eventId());
    }

    private MvcTestResult place(String sku, int quantity) {
        return mvc.post()
                .uri("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"customerId":"alice","lines":[{"sku":"%s","quantity":%d}]}""".formatted(sku, quantity))
                .exchange();
    }
}
