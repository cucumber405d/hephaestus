package de.ibikus.appa.hephaestus.modernjava.java11;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PriceListParserTest {

    @Test
    void skipsCommentsAndBlankLinesAndStripsValues() {
        var text = "# prices\nBRZ; 12.50 \n\n  \nIRN;3.20";

        assertThat(PriceListParser.parse(text))
                .isEqualTo(Map.of("BRZ", new BigDecimal("12.50"), "IRN", new BigDecimal("3.20")));
    }

    @Test
    void stringRepeatBuildsSeparator() {
        assertThat("-".repeat(5)).isEqualTo("-----");
    }

    @Test
    void readsPriceListFromFile(@TempDir Path dir) throws Exception {
        var file = Files.writeString(dir.resolve("prices.txt"), "STL;4.80\n");

        assertThat(PriceListParser.parse(Files.readString(file))).containsEntry("STL", new BigDecimal("4.80"));
    }
}
