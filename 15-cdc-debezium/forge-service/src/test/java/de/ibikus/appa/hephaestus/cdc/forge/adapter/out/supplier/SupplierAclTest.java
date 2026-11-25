package de.ibikus.appa.hephaestus.cdc.forge.adapter.out.supplier;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import de.ibikus.appa.hephaestus.cdc.forge.domain.SupplyResult.Delivered;
import de.ibikus.appa.hephaestus.cdc.forge.domain.SupplyResult.PermanentFailure;
import de.ibikus.appa.hephaestus.cdc.forge.domain.SupplyResult.TransientFailure;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The ACL's translation table: codes in, Forge's words out; slowness and nonsense are transient. */
class SupplierAclTest {

    HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void status0IsDelivered() {
        assertThat(gateway(answer(0, 5), 0, Duration.ofSeconds(1)).order("IRON-01", 5))
                .isEqualTo(new Delivered(5));
    }

    @Test
    void status42IsPermanentlyNotDeliverable() {
        assertThat(gateway(answer(42, 0), 0, Duration.ofSeconds(1)).order("MITHRIL-01", 1))
                .isEqualTo(new PermanentFailure(PermanentFailure.NOT_DELIVERABLE));
    }

    @Test
    void status17IsAnUnknownArticle() {
        assertThat(gateway(answer(17, 0), 0, Duration.ofSeconds(1)).order("FOO", 1))
                .isEqualTo(new PermanentFailure(PermanentFailure.UNKNOWN_ARTICLE));
    }

    @Test
    void anAnswerSlowerThanTheTimeoutIsTransient() {
        assertThat(gateway(answer(0, 5), 3_000, Duration.ofSeconds(1)).order("IRON-01", 5))
                .isInstanceOf(TransientFailure.class);
    }

    @Test
    void nonsenseIsTransient() {
        assertThat(gateway("<html>maintenance</html>", 0, Duration.ofSeconds(1)).order("IRON-01", 5))
                .isInstanceOf(TransientFailure.class);
    }

    @Test
    void anUnreachableSupplierIsTransient() {
        assertThat(new HttpSupplier("http://127.0.0.1:1", Duration.ofSeconds(1)).order("IRON-01", 5))
                .isInstanceOf(TransientFailure.class);
    }

    private HttpSupplier gateway(String body, long delayMs, Duration timeout) {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/lieferant/bestellung", exchange -> {
            exchange.getRequestBody().readAllBytes();
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            var bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return new HttpSupplier("http://127.0.0.1:" + server.getAddress().getPort(), timeout);
    }

    private static String answer(int status, int menge) {
        return "<Antwort><Status>" + status + "</Status><Liefermenge>" + menge + "</Liefermenge></Antwort>";
    }
}
