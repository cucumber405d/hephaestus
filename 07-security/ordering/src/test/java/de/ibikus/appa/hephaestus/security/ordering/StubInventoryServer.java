package de.ibikus.appa.hephaestus.security.ordering;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A stand-in for Inventory on a random local port, built on the JDK's HTTP server (no extra test dependency).
 * Tests set the canned answer per path prefix and read what the client actually sent. It can also play the
 * Keycloak token endpoint: answer "/token" with {@link Answer#token(String)}.
 */
public final class StubInventoryServer implements AutoCloseable {

    public record Answer(int status, String contentType, String body, Duration delay) {

        public static Answer json(int status, String body) {
            return new Answer(status, "application/json", body, Duration.ZERO);
        }

        public static Answer problem(int status, String body) {
            return new Answer(status, "application/problem+json", body, Duration.ZERO);
        }

        /** A client-credentials token response as Keycloak sends it. */
        public static Answer token(String accessToken) {
            return json(200, """
                    {"access_token":"%s","token_type":"Bearer","expires_in":300}""".formatted(accessToken));
        }

        public Answer delayedBy(Duration delay) {
            return new Answer(status, contentType, body, delay);
        }
    }

    public record Received(String method, String path, String apiVersion, String authorization, String body) {}

    private final HttpServer server;
    private final Map<String, Answer> answers = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> calls = new ConcurrentHashMap<>();
    private volatile Received lastRequest;

    public StubInventoryServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/", this::handle);
        server.start();
    }

    public URI url() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    /** Answer for requests whose path starts with the prefix, e.g. "/api/reservations". */
    public void answer(String pathPrefix, Answer answer) {
        answers.put(pathPrefix, answer);
    }

    public Received lastRequest() {
        return lastRequest;
    }

    /** How many requests hit exactly this path, e.g. "/token". */
    public int calls(String path) {
        var count = calls.get(path);
        return count == null ? 0 : count.get();
    }

    private void handle(HttpExchange exchange) throws IOException {
        var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        var path = exchange.getRequestURI().getPath();
        calls.computeIfAbsent(path, any -> new AtomicInteger()).incrementAndGet();
        var headers = exchange.getRequestHeaders();
        var received = new Received(
                exchange.getRequestMethod(),
                path,
                headers.getFirst("API-Version"),
                headers.getFirst("Authorization"),
                body);
        // The token request is plumbing; lastRequest stays the call to Inventory itself
        if (!path.equals("/token")) {
            lastRequest = received;
        }
        var answer = answers.entrySet().stream()
                .filter(entry -> path.startsWith(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(Answer.json(404, "{}"));
        try {
            Thread.sleep(answer.delay());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        var bytes = answer.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", answer.contentType());
        try {
            exchange.sendResponseHeaders(answer.status(), bytes.length);
            exchange.getResponseBody().write(bytes);
        } catch (IOException clientGone) {
            // the client hit its read timeout and closed the connection
        } finally {
            exchange.close();
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
