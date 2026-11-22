package de.ibikus.appa.hephaestus.monolith.traffic;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

/** A JDK HttpServer answering from a queue of (status, body); records "METHOD path" of every request. */
final class FakeServer implements AutoCloseable {

    record Answer(int status, String body) {}

    final List<String> requests = new CopyOnWriteArrayList<>();
    final Queue<Answer> answers = new ConcurrentLinkedQueue<>();
    private final HttpServer server;
    private volatile Answer fallback = new Answer(200, "");

    FakeServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
            var answer = answers.isEmpty() ? fallback : answers.poll();
            var bytes = answer.body().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(answer.status(), bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
        server.start();
    }

    FakeServer otherwise(int status, String body) {
        fallback = new Answer(status, body);
        return this;
    }

    FakeServer then(int status, String body) {
        answers.add(new Answer(status, body));
        return this;
    }

    String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
