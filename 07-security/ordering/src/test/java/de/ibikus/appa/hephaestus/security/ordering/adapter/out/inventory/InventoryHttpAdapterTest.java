package de.ibikus.appa.hephaestus.security.ordering.adapter.out.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.ibikus.appa.hephaestus.security.ordering.StubInventoryServer;
import de.ibikus.appa.hephaestus.security.ordering.StubInventoryServer.Answer;
import de.ibikus.appa.hephaestus.security.ordering.domain.InsufficientStockException;
import de.ibikus.appa.hephaestus.security.ordering.domain.InventoryUnavailableException;
import de.ibikus.appa.hephaestus.security.ordering.domain.OrderLine;
import de.ibikus.appa.hephaestus.security.ordering.domain.UnknownItemException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/** The real HTTP client (proxy, RestClient, JDK HttpClient) against a stub server: wire format and failures. */
class InventoryHttpAdapterTest {

    private static final List<OrderLine> ONE_ANVIL = List.of(new OrderLine("ANVIL-01", 1));

    private StubInventoryServer inventory;
    private InventoryHttpAdapter adapter;

    @BeforeEach
    void start() throws Exception {
        inventory = new StubInventoryServer();
        adapter = adapterFor(inventory.url(), Duration.ofMillis(500));
    }

    @AfterEach
    void stop() {
        inventory.close();
    }

    @Test
    void reservePostsTheOrderWithTheCurrentApiVersionAndReturnsTheReservationId() {
        var reservationId = UUID.randomUUID();
        var orderId = UUID.randomUUID();
        inventory.answer("/api/reservations", Answer.json(201, """
                        {"id":"%s","orderId":"%s","lines":[{"sku":"ANVIL-01","quantity":1}],"createdAt":"2026-11-17T10:00:00Z"}""".formatted(reservationId, orderId)));

        var result = adapter.reserve(orderId, ONE_ANVIL);

        assertThat(result).isEqualTo(reservationId);
        assertThat(inventory.lastRequest().method()).isEqualTo("POST");
        assertThat(inventory.lastRequest().path()).isEqualTo("/api/reservations");
        assertThat(inventory.lastRequest().apiVersion()).isEqualTo("2");
        assertThat(inventory.lastRequest().body()).contains(orderId.toString()).contains("\"ANVIL-01\"");
    }

    @Test
    void stockUsesApiVersionTwo() {
        inventory.answer("/api/stock/", Answer.json(200, """
                        {"sku":"ANVIL-01","name":"Anvil","onHand":10,"reserved":3,"available":7}"""));

        assertThat(adapter.available("ANVIL-01")).isEqualTo(7);
        assertThat(inventory.lastRequest().path()).isEqualTo("/api/stock/ANVIL-01");
        assertThat(inventory.lastRequest().apiVersion()).isEqualTo("2");
    }

    @Test
    void conflictBecomesInsufficientStockForTheSkuInventoryNamed() {
        inventory.answer("/api/reservations", Answer.problem(409, """
                        {"type":"https://hephaestus.ibikus.de/problems/insufficient-stock","status":409,"sku":"SWORD-01","available":0}"""));
        var lines = List.of(new OrderLine("ANVIL-01", 1), new OrderLine("SWORD-01", 5));

        assertThatThrownBy(() -> adapter.reserve(UUID.randomUUID(), lines))
                .isInstanceOfSatisfying(
                        InsufficientStockException.class,
                        ex -> assertThat(ex.sku()).isEqualTo("SWORD-01"));
    }

    @Test
    void unprocessableBecomesUnknownItem() {
        inventory.answer("/api/reservations", Answer.problem(422, """
                        {"type":"https://hephaestus.ibikus.de/problems/unknown-sku","status":422,"sku":"NOPE-01"}"""));

        assertThatThrownBy(() -> adapter.reserve(UUID.randomUUID(), List.of(new OrderLine("NOPE-01", 1))))
                .isInstanceOfSatisfying(
                        UnknownItemException.class, ex -> assertThat(ex.sku()).isEqualTo("NOPE-01"));
    }

    @Test
    void stockNotFoundBecomesUnknownItem() {
        inventory.answer("/api/stock/", Answer.problem(404, "{\"status\":404}"));

        assertThatThrownBy(() -> adapter.available("NOPE-01")).isInstanceOf(UnknownItemException.class);
    }

    @Test
    void serverErrorBecomesUnavailable() {
        inventory.answer("/api/reservations", Answer.problem(500, "{\"status\":500}"));

        assertThatThrownBy(() -> adapter.reserve(UUID.randomUUID(), ONE_ANVIL))
                .isInstanceOf(InventoryUnavailableException.class);
    }

    @Test
    void refusedConnectionBecomesUnavailable() {
        var unreachable = adapterFor(URI.create("http://127.0.0.1:1"), Duration.ofMillis(500));

        assertThatThrownBy(() -> unreachable.reserve(UUID.randomUUID(), ONE_ANVIL))
                .isInstanceOf(InventoryUnavailableException.class);
    }

    @Test
    void slowAnswerHitsTheReadTimeout() {
        inventory.answer("/api/reservations", Answer.json(201, "{}").delayedBy(Duration.ofSeconds(3)));
        var started = System.nanoTime();

        assertThatThrownBy(() -> adapter.reserve(UUID.randomUUID(), ONE_ANVIL))
                .isInstanceOf(InventoryUnavailableException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(2));
    }

    private static InventoryHttpAdapter adapterFor(URI url, Duration readTimeout) {
        var properties = new InventoryClientProperties(url, Duration.ofMillis(500), readTimeout);
        return new InventoryHttpAdapter(InventoryClientConfiguration.create(RestClient.builder(), properties));
    }
}
