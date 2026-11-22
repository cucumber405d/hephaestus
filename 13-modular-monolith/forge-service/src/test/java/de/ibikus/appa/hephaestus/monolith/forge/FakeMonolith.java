package de.ibikus.appa.hephaestus.monolith.forge;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/** The monolith's {@code /internal/work-orders} on a JDK HttpServer; {@link #up} false answers 503. */
public final class FakeMonolith {

    public static volatile boolean up = true;
    public static final String OPEN_WORK_ORDERS = """
            [{"id":"WO-000042","orderId":"00000000-0000-0000-0000-000000000042","material":"IRON-01","quantity":2,\
            "status":"IN_PROGRESS","origin":"MONOLITH"}]""";

    private static HttpServer server;

    private FakeMonolith() {}

    public static synchronized String url() {
        if (server == null) {
            try {
                server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            server.createContext("/internal/work-orders", exchange -> {
                var bytes = OPEN_WORK_ORDERS.getBytes(StandardCharsets.UTF_8);
                if (up) {
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                } else {
                    exchange.sendResponseHeaders(503, -1);
                }
                exchange.close();
            });
            server.start();
        }
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
