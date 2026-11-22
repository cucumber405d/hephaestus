package de.ibikus.appa.hephaestus.monolith.traffic;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * A small HTTP helper: an optional {@code Host} header (to go through the Gateway by IP, as 12's --resolve) and
 * retries on 5xx and I/O errors. 4xx are answers, not failures: the caller decides.
 */
final class Http {

    record Response(int status, String body) {}

    private final HttpClient client;
    private final String host;
    private final int attempts;

    Http(String host, int attempts) {
        // must be set before the first HttpClient is built
        System.setProperty("jdk.httpclient.allowRestrictedHeaders", "host");
        this.client =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.host = host == null || host.isBlank() ? null : host;
        this.attempts = attempts;
    }

    Response get(String url) {
        return send(request(url).GET().build());
    }

    Response post(String url, String json) {
        return send(request(url)
                .header("Content-Type", "application/json")
                .POST(json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json))
                .build());
    }

    private HttpRequest.Builder request(String url) {
        var builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10));
        if (host != null) {
            builder.header("Host", host);
        }
        return builder;
    }

    private Response send(HttpRequest request) {
        RuntimeException last = null;
        for (var attempt = 1; attempt <= attempts; attempt++) {
            try {
                var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() < 500) {
                    return new Response(response.statusCode(), response.body());
                }
                last = new IllegalStateException(request.uri() + " -> " + response.statusCode());
            } catch (IOException e) {
                last = new IllegalStateException(request.uri() + " -> " + e, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            pause(200L * attempt);
        }
        throw last;
    }

    static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
