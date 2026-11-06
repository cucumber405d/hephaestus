package de.ibikus.appa.hephaestus.modernjava.java16;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void normalizesScaleToCents() {
        assertThat(Money.eur("1.5").amount().toPlainString()).isEqualTo("1.50");
    }

    @Test
    void rejectsNegativeAmount() {
        assertThatThrownBy(() -> new Money(new BigDecimal("-1"), "EUR")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidCurrency() {
        assertThatThrownBy(() -> new Money(BigDecimal.ONE, "EU")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addsSameCurrency() {
        assertThat(Money.eur("1.20").plus(Money.eur("2.30"))).isEqualTo(Money.eur("3.50"));
    }

    @Test
    void refusesToAddDifferentCurrencies() {
        assertThatThrownBy(() -> Money.eur("1").plus(Money.of("1", "USD")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recordsHaveValueSemantics() {
        assertThat(Money.eur("2")).isEqualTo(Money.eur("2.00")).hasSameHashCodeAs(Money.eur("2.00"));
    }
}
