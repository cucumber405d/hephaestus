package de.ibikus.appa.hephaestus.resilience.inventory;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The supplier for the comparison IT, on the host (Toxiproxy in its container reaches it via
 * host.testcontainers.internal). Any SKU has an offer except NOPE-*; counts calls and the most it served at once.
 */
public final class StubSupplierServer implements AutoCloseable {

    public enum Mode {
        HEALTHY,
        FAILING,
        SLOW
    }

    private final HttpServer server;
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicInteger maxInFlight = new AtomicInteger();
    private volatile Mode mode = Mode.HEALTHY;

    public StubSupplierServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("0.0.0.0", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/api/offers/", this::offer);
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public void mode(Mode mode) {
        this.mode = mode;
    }

    public int calls() {
        return calls.get();
    }

    public int maxInFlight() {
        return maxInFlight.get();
    }

    public void resetCounters() {
        calls.set(0);
        maxInFlight.set(0);
    }

    private void offer(HttpExchange exchange) throws IOException {
        calls.incrementAndGet();
        maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
        try {
            var sku = exchange.getRequestURI().getPath().substring("/api/offers/".length());
            if (mode == Mode.SLOW) {
                // Slow but within the client's 1 s read timeout: the client does not give up and retry while we
                // still work, so maxInFlight measures what the mechanism lets through
                Thread.sleep(900);
            }
            if (mode == Mode.FAILING) {
                respond(exchange, 503, "{\"status\":503}");
            } else if (sku.startsWith("NOPE-")) {
                respond(exchange, 404, "{\"status\":404}");
            } else {
                respond(exchange, 200, """
                        {"sku":"%s","price":120.00,"currency":"EUR","leadTimeDays":5,"maxQuantity":20}""".formatted(sku));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException clientGone) {
            // the client gave up (read timeout)
        } finally {
            inFlight.decrementAndGet();
            exchange.close();
        }
    }

    private static void respond(HttpExchange exchange, int status, String json) throws IOException {
        var bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
