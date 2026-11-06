package de.ibikus.appa.hephaestus.modernjava.java12;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderStatisticsTest {

    @Test
    void summarizesTotalAndAverageInOnePass() {
        var summary =
                OrderStatistics.summarize(List.of(new BigDecimal("10"), new BigDecimal("20"), new BigDecimal("30")));

        assertThat(summary.total()).isEqualByComparingTo("60");
        assertThat(summary.average()).isEqualByComparingTo("20");
    }

    @Test
    void emptyOrderDoesNotDivideByZero() {
        var summary = OrderStatistics.summarize(List.of());

        assertThat(summary.total()).isEqualByComparingTo("0");
        assertThat(summary.average()).isEqualByComparingTo("0");
    }

    @Test
    void indentShiftsEveryLine() {
        assertThat("BRZ\nIRN".indent(2)).isEqualTo("  BRZ\n  IRN\n");
    }

    @Test
    void labelUsesTransform() {
        assertThat(OrderStatistics.label("  a-17 ")).isEqualTo("ORDER A-17");
    }
}
