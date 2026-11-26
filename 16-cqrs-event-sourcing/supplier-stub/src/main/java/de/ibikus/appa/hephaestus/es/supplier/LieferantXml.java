package de.ibikus.appa.hephaestus.es.supplier;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;

/**
 * The supplier's wire format: {@code <Bestellung><ArtNr/><Menge/></Bestellung>} in,
 * {@code <Antwort><Status/><Liefermenge/></Antwort>} out. Parsed with the JDK's DOM parser, external entities off.
 */
final class LieferantXml {

    record Bestellung(String artNr, int menge) {}

    private LieferantXml() {}

    static Bestellung parse(String xml) {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document doc =
                    factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            var artNr =
                    doc.getElementsByTagName("ArtNr").item(0).getTextContent().trim();
            var menge = Integer.parseInt(
                    doc.getElementsByTagName("Menge").item(0).getTextContent().trim());
            return new Bestellung(artNr, menge);
        } catch (Exception e) {
            throw new IllegalArgumentException("malformed Bestellung", e);
        }
    }

    static String antwort(int status, int liefermenge) {
        return "<Antwort><Status>" + status + "</Status><Liefermenge>" + liefermenge + "</Liefermenge></Antwort>";
    }
}
