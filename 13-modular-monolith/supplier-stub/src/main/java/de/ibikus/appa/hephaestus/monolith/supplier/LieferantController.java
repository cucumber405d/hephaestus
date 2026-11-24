package de.ibikus.appa.hephaestus.monolith.supplier;

import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /lieferant/bestellung}: status 0 delivered (the full quantity), 17 unknown article, 42 not deliverable
 * (MITHRIL-01, always). {@code X-Verzoegerung-Ms} delays the answer, to play the slow supplier.
 */
@RestController
class LieferantController {

    static final int GELIEFERT = 0;
    static final int UNBEKANNT = 17;
    static final int NICHT_LIEFERBAR = 42;

    private static final Set<String> SORTIMENT = Set.of("IRON-01", "STEEL-01", "MITHRIL-01");

    @PostMapping(
            value = "/lieferant/bestellung",
            consumes = MediaType.APPLICATION_XML_VALUE,
            produces = MediaType.APPLICATION_XML_VALUE)
    String bestellen(
            @RequestBody String body, @RequestHeader(name = "X-Verzoegerung-Ms", required = false) Long verzoegerungMs)
            throws InterruptedException {
        var bestellung = LieferantXml.parse(body);
        if (verzoegerungMs != null && verzoegerungMs > 0) {
            Thread.sleep(verzoegerungMs);
        }
        if (!SORTIMENT.contains(bestellung.artNr())) {
            return LieferantXml.antwort(UNBEKANNT, 0);
        }
        if (bestellung.artNr().equals("MITHRIL-01")) {
            return LieferantXml.antwort(NICHT_LIEFERBAR, 0);
        }
        return LieferantXml.antwort(GELIEFERT, bestellung.menge());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    String malformed() {
        return "<Fehler>Bestellung unlesbar</Fehler>";
    }
}
