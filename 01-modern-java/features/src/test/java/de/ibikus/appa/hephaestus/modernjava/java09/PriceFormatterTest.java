package de.ibikus.appa.hephaestus.modernjava.java09;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PriceFormatterTest {

    private final PriceFormatter formatter = new PriceFormatter() {};

    @Test
    void formatsWithTwoDecimalsAndCurrency() {
        assertThat(formatter.format(new BigDecimal("12.5"))).isEqualTo("12.50 EUR");
    }

    @Test
    void roundsHalfUp() {
        assertThat(formatter.format(new BigDecimal("3.205"))).isEqualTo("3.21 EUR");
    }
}
