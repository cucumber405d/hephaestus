package de.ibikus.appa.hephaestus.modernjava.java16;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class OrderLineTest {

    @Test
    void totalIsUnitPriceTimesQuantity() {
        assertThat(new OrderLine("BRZ", 3, Money.eur("12.50")).total()).isEqualTo(Money.eur("37.50"));
    }

    @Test
    void rejectsNonPositiveQuantity() {
        assertThatThrownBy(() -> new OrderLine("BRZ", 0, Money.eur("1"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void expandsLinesIntoUnitsWithMapMulti() {
        var units = OrderLine.expandUnits(
                List.of(new OrderLine("BRZ", 2, Money.eur("1")), new OrderLine("IRN", 1, Money.eur("1"))));

        assertThat(units).containsExactly("BRZ", "BRZ", "IRN");
        assertThatThrownBy(() -> units.add("X")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void describesOnlyKnownTypesWithInstanceofPattern() {
        assertThat(OrderLine.describe(new OrderLine("BRZ", 3, Money.eur("1")))).isEqualTo("3 x BRZ");
        assertThat(OrderLine.describe(Money.eur("12.5"))).isEqualTo("12.50 EUR");
        assertThat(OrderLine.describe("something")).isEqualTo("unknown");
    }
}
