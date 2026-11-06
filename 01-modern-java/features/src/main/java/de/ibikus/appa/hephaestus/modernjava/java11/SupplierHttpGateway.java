package de.ibikus.appa.hephaestus.modernjava.java11;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Java 11 standard {@code java.net.http.HttpClient} (JEP 321). */
public final class SupplierHttpGateway {

    private final URI baseUri;
    private final HttpClient client;

    public SupplierHttpGateway(URI baseUri) {
        this.baseUri = baseUri;
        this.client =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    }

    /** Fetches {@code GET {base}/prices/{code}}; the response body is the price. */
    public BigDecimal price(String code) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(baseUri.resolve("/prices/" + code))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Supplier answered " + response.statusCode() + " for " + code);
        }
        return new BigDecimal(response.body().strip());
    }
}
