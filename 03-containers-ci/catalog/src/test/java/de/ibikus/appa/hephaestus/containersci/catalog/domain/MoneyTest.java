package de.ibikus.appa.hephaestus.containersci.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
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
        assertThatThrownBy(() -> Money.of("1e2", "EUR")).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> Money.of(" 1", "EUR")).isInstanceOf(InvalidValueException.class);
    }

    @Test
    void rejectsMoreThanTenIntegerDigits() {
        assertThat(Money.of("9999999999.99", "EUR").amount().toPlainString()).isEqualTo("9999999999.99");
        assertThatThrownBy(() -> Money.of("10000000000", "EUR")).isInstanceOf(InvalidValueException.class);
    }

    /** setScale on a huge exponent would allocate a billion-digit number; the format check must come first. */
    @Test
    void hugeExponentIsRejectedQuickly() {
        assertTimeoutPreemptively(
                Duration.ofSeconds(1),
                () -> assertThatThrownBy(() -> Money.of("1E+999999999", "EUR"))
                        .isInstanceOf(InvalidValueException.class));
    }

    @Test
    void equalAmountsWithDifferentScaleAreEqual() {
        assertThat(Money.of("2", "EUR")).isEqualTo(Money.of("2.00", "EUR"));
    }
}
