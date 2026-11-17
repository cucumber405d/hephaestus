package de.ibikus.appa.hephaestus.servicecommunication.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** The real gateway on a random port in front of two stub backends. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutingIT {

    static final StubBackend ORDERING = new StubBackend("ordering");
    static final StubBackend INVENTORY = new StubBackend("inventory");
    static final HttpClient HTTP = HttpClient.newHttpClient();

    @DynamicPropertySource
    static void backends(DynamicPropertyRegistry registry) {
        registry.add("ORDERING_URL", ORDERING::url);
        registry.add("INVENTORY_URL", INVENTORY::url);
    }

    @AfterAll
    static void stopBackends() {
        ORDERING.close();
        INVENTORY.close();
    }

    @Value("${local.server.port}")
    int port;

    @Test
    void ordersGoToOrdering() throws Exception {
        var response = send(HttpRequest.newBuilder(uri("/api/orders/42")).GET());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("ordering");
        assertThat(ORDERING.lastRequest().path()).isEqualTo("/api/orders/42");
        assertThat(response.headers().firstValue("X-Gateway")).contains("hephaestus");
    }

    @Test
    void requestBodyIsForwarded() throws Exception {
        var json = "{\"lines\":[{\"sku\":\"ANVIL-01\",\"quantity\":1}]}";

        send(HttpRequest.newBuilder(uri("/api/orders"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)));

        assertThat(ORDERING.lastRequest().method()).isEqualTo("POST");
        assertThat(ORDERING.lastRequest().body()).isEqualTo(json);
    }

    @Test
    void stockGoesToInventoryWithTheApiVersionHeader() throws Exception {
        var response = send(HttpRequest.newBuilder(uri("/api/stock/ANVIL-01"))
                .header("API-Version", "2")
                .GET());

        assertThat(response.body()).isEqualTo("inventory");
        assertThat(INVENTORY.lastRequest().path()).isEqualTo("/api/stock/ANVIL-01");
        assertThat(INVENTORY.lastRequest().apiVersion()).isEqualTo("2");
    }

    @Test
    void internalReservationApiIsNotExposed() throws Exception {
        var before = INVENTORY.lastRequest();

        var response = send(HttpRequest.newBuilder(uri("/api/reservations"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}")));

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(INVENTORY.lastRequest()).isSameAs(before);
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private static HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
