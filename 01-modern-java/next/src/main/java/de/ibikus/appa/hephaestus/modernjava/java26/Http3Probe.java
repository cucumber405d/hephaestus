package de.ibikus.appa.hephaestus.modernjava.java26;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Java 26 HTTP/3 for the HTTP Client (JEP 517): HTTP/3 runs over QUIC (UDP). Requesting {@code HTTP_3} makes the
 * client try it and fall back to HTTP/2 or 1.1 when the server or the network does not support it.
 */
public final class Http3Probe {

    private Http3Probe() {}

    public static HttpResponse<String> fetch(URI uri) throws Exception {
        try (var client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_3)
                .connectTimeout(Duration.ofSeconds(5))
                .build()) {
            var request =
                    HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET().build();
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }
}
