package de.ibikus.appa.hephaestus.monolith.forge.adapter.out.supplier;

import de.ibikus.appa.hephaestus.monolith.forge.application.Supplier;
import de.ibikus.appa.hephaestus.monolith.forge.domain.SupplyResult;
import de.ibikus.appa.hephaestus.monolith.forge.domain.SupplyResult.Delivered;
import de.ibikus.appa.hephaestus.monolith.forge.domain.SupplyResult.PermanentFailure;
import de.ibikus.appa.hephaestus.monolith.forge.domain.SupplyResult.TransientFailure;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * The service's own anti-corruption layer (a copy of the monolith's, not a shared library). Translates the supplier's legacy protocol ({@code Bestellung}/{@code Antwort}, status 0/17/42) into
 * {@link SupplyResult}. Unlike the legacy, every call has a timeout and a failure is an answer, not an exception.
 */
@Component
public class HttpSupplier implements Supplier {

    static final int GELIEFERT = 0;
    static final int UNBEKANNT = 17;
    static final int NICHT_LIEFERBAR = 42;

    private static final Pattern STATUS = Pattern.compile("<Status>(\\d+)</Status>");
    private static final Pattern MENGE = Pattern.compile("<Liefermenge>(\\d+)</Liefermenge>");

    private final RestClient http;

    public HttpSupplier(@Value("${supplier.url}") String url, @Value("${supplier.timeout}") Duration timeout) {
        var factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(timeout).build());
        factory.setReadTimeout(timeout);
        this.http = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    @Override
    public SupplyResult order(String material, int quantity) {
        String xml;
        try {
            xml = http.post()
                    .uri("/lieferant/bestellung")
                    .contentType(MediaType.APPLICATION_XML)
                    .body("<Bestellung><ArtNr>" + material + "</ArtNr><Menge>" + quantity + "</Menge></Bestellung>")
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            return new TransientFailure("supplier unreachable: " + e.getMessage());
        }
        var status = number(STATUS, xml);
        return switch (status) {
            case GELIEFERT -> new Delivered(number(MENGE, xml));
            case NICHT_LIEFERBAR -> new PermanentFailure(PermanentFailure.NOT_DELIVERABLE);
            case UNBEKANNT -> new PermanentFailure(PermanentFailure.UNKNOWN_ARTICLE);
            default -> new TransientFailure("unexpected supplier answer: " + xml);
        };
    }

    private static int number(Pattern pattern, String xml) {
        var m = pattern.matcher(xml == null ? "" : xml);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }
}
