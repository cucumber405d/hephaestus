package de.ibikus.appa.hephaestus.contracts.supplier;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** The legacy protocol as the two ACLs will see it: status 0 delivered, 17 unknown article, 42 not deliverable. */
@WebMvcTest(LieferantController.class)
class LieferantControllerTest {

    @Autowired
    MockMvcTester mvc;

    @Test
    void ironIsDelivered() {
        assertThat(order("IRON-01", 5))
                .hasStatusOk()
                .bodyText()
                .contains("<Status>0</Status>")
                .contains("<Liefermenge>5</Liefermenge>");
    }

    @Test
    void mithrilIsNeverDeliverable() {
        assertThat(order("MITHRIL-01", 3))
                .hasStatusOk()
                .bodyText()
                .contains("<Status>42</Status>")
                .contains("<Liefermenge>0</Liefermenge>");
    }

    @Test
    void anUnknownArticleIsCode17() {
        assertThat(order("FOO", 1)).hasStatusOk().bodyText().contains("<Status>17</Status>");
    }

    @Test
    void malformedXmlIsBadRequest() {
        assertThat(mvc.post()
                        .uri("/lieferant/bestellung")
                        .contentType(MediaType.APPLICATION_XML)
                        .content("<Bestellung><ArtNr>"))
                .hasStatus(400);
    }

    private MvcTestResult order(String article, int quantity) {
        return mvc.post()
                .uri("/lieferant/bestellung")
                .contentType(MediaType.APPLICATION_XML)
                .content("<Bestellung><ArtNr>" + article + "</ArtNr><Menge>" + quantity + "</Menge></Bestellung>")
                .exchange();
    }
}
