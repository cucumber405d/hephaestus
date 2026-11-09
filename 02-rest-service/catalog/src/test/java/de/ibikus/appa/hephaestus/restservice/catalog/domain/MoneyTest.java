package de.ibikus.appa.hephaestus.restservice.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void normalizesToTwoFractionDigits() {
        assertThat(Money.of("12.5", "EUR").amount().toPlainString()).isEqualTo("12.50");
        assertThat(Money.of("7", "EUR").amount().toPlainString()).isEqualTo("7.00");
    }

    @Test
    void rejectsMoreThanTwoFractionDigitsInsteadOfRounding() {
        assertThatThrownBy(() -> Money.of("12.345", "EUR")).isInstanceOf(InvalidValueException.class);
    }

    @Test
    void rejectsNegativeAmount() {
        assertThatThrownBy(() -> Money.of("-1", "EUR")).isInstanceOf(InvalidValueException.class);
    }

    @Test
    void rejectsInvalidCurrency() {
        assertThatThrownBy(() -> Money.of("1", "eur")).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> Money.of("1", "EURO")).isInstanceOf(InvalidValueException.class);
    }

    @Test
    void rejectsMalformedAmount() {
        assertThatThrownBy(() -> Money.of("twelve", "EUR")).isInstanceOf(InvalidValueException.class);
    }

    @Test
    void equalAmountsWithDifferentScaleAreEqual() {
        assertThat(Money.of("2", "EUR")).isEqualTo(Money.of("2.00", "EUR"));
    }
}
