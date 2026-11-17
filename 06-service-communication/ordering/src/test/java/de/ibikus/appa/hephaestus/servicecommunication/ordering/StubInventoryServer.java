package de.ibikus.appa.hephaestus.servicecommunication.ordering;

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

/**
 * A stand-in for Inventory on a random local port, built on the JDK's HTTP server (no extra test dependency).
 * Tests set the canned answer per path prefix and read what the client actually sent.
 */
public final class StubInventoryServer implements AutoCloseable {

    public record Answer(int status, String contentType, String body, Duration delay) {

        public static Answer json(int status, String body) {
            return new Answer(status, "application/json", body, Duration.ZERO);
        }

        public static Answer problem(int status, String body) {
            return new Answer(status, "application/problem+json", body, Duration.ZERO);
        }

        public Answer delayedBy(Duration delay) {
            return new Answer(status, contentType, body, delay);
        }
    }

    public record Received(String method, String path, String apiVersion, String body) {}

    private final HttpServer server;
    private final Map<String, Answer> answers = new ConcurrentHashMap<>();
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

    private void handle(HttpExchange exchange) throws IOException {
        var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        var path = exchange.getRequestURI().getPath();
        lastRequest = new Received(
                exchange.getRequestMethod(), path, exchange.getRequestHeaders().getFirst("API-Version"), body);
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
