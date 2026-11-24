package de.ibikus.appa.hephaestus.monolith.legacy.supplier;

import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** The supplier over HTTP, no timeout — the legacy waits as long as the supplier wants. */
@Component
public class SupplierClient {

    private static final Pattern STATUS = Pattern.compile("<Status>(\\d+)</Status>");
    private static final Pattern MENGE = Pattern.compile("<Liefermenge>(\\d+)</Liefermenge>");

    private final RestClient http;

    SupplierClient(RestClient.Builder builder, @Value("${SUPPLIER_URL:http://localhost:8081}") String url) {
        this.http = builder.baseUrl(url).build();
    }

    public Antwort bestellen(Bestellung bestellung) {
        var xml = http.post()
                .uri("/lieferant/bestellung")
                .contentType(MediaType.APPLICATION_XML)
                .body("<Bestellung><ArtNr>" + bestellung.artNr() + "</ArtNr><Menge>" + bestellung.menge()
                        + "</Menge></Bestellung>")
                .retrieve()
                .body(String.class);
        return new Antwort(number(STATUS, xml), number(MENGE, xml));
    }

    private static int number(Pattern pattern, String xml) {
        var m = pattern.matcher(xml == null ? "" : xml);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }
}
