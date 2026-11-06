package de.ibikus.appa.hephaestus.modernjava.java11;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SupplierHttpGatewayTest {

    private HttpServer server;

    @BeforeEach
    void startStubSupplier() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/prices/", exchange -> {
            var body = "4.80".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
    }

    @AfterEach
    void stopStubSupplier() {
        server.stop(0);
    }

    @Test
    void fetchesPriceOverHttp() throws Exception {
        var gateway = new SupplierHttpGateway(
                URI.create("http://localhost:" + server.getAddress().getPort()));

        assertThat(gateway.price("STL")).isEqualByComparingTo(new BigDecimal("4.80"));
    }
}
