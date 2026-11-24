package de.ibikus.appa.hephaestus.monolith.legacy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * The supplier's legacy protocol on a JDK HttpServer: IRON-01/STEEL-01 delivered in full (status 0), MITHRIL-01
 * never (42), anything else unknown (17). Started once per test JVM.
 */
public final class FakeSupplier {

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
            server.createContext("/lieferant/bestellung", exchange -> {
                var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                var art = find(ART, body);
                var menge = Integer.parseInt(find(MENGE, body));
                String answer;
                if (art.equals("IRON-01") || art.equals("STEEL-01")) {
                    answer = antwort(0, menge);
                } else if (art.equals("MITHRIL-01")) {
                    answer = antwort(42, 0);
                } else {
                    answer = antwort(17, 0);
                }
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

    private static String antwort(int status, int menge) {
        return "<Antwort><Status>" + status + "</Status><Liefermenge>" + menge + "</Liefermenge></Antwort>";
    }
}
