package de.ibikus.appa.hephaestus.modernjava.java15;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class OrderTemplatesTest {

    @Test
    void rendersJsonFromTextBlock() {
        var json = OrderTemplates.orderJson("A-1", new BigDecimal("37.50"));

        assertThat(json).contains("\"id\": \"A-1\"").contains("\"total\": 37.50");
        assertThat(json.lines()).first().isEqualTo("{");
    }
}
