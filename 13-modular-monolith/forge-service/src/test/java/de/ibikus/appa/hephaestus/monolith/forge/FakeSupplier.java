package de.ibikus.appa.hephaestus.monolith.forge;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * The supplier's legacy protocol on a JDK HttpServer: IRON-01/STEEL-01 delivered (0), MITHRIL-01 never (42), anything
 * else unknown (17). {@link #delayMs} makes it slow, for the timeout cases. One server per test JVM.
 */
public final class FakeSupplier {

    public static volatile long delayMs = 0;

    private static final Pattern ART = Pattern.compile("<ArtNr>(.*?)</ArtNr>");
    private static final Pattern MENGE = Pattern.compile("<Menge>(\\d+)</Menge>");
    private static HttpServer server;

    private FakeSupplier() {}

    public static synchronized String url() {
        if (server == null) {
            try {
                server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            server.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
            server.createContext("/lieferant/bestellung", exchange -> {
                var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                var art = find(ART, body);
                var menge = Integer.parseInt(find(MENGE, body));
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                var status = switch (art) {
                    case "IRON-01", "STEEL-01" -> 0;
                    case "MITHRIL-01" -> 42;
                    default -> 17;
                };
                var answer = "<Antwort><Status>" + status + "</Status><Liefermenge>" + (status == 0 ? menge : 0)
                        + "</Liefermenge></Antwort>";
                var bytes = answer.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/xml");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.start();
        }
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static String find(Pattern pattern, String body) {
        var m = pattern.matcher(body);
        return m.find() ? m.group(1) : "";
    }
}
