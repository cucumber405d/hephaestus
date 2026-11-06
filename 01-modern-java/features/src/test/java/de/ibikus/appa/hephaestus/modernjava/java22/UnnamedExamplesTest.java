package de.ibikus.appa.hephaestus.modernjava.java22;

import static org.assertj.core.api.Assertions.assertThat;

import de.ibikus.appa.hephaestus.modernjava.java16.Money;
import de.ibikus.appa.hephaestus.modernjava.java16.OrderLine;
import de.ibikus.appa.hephaestus.modernjava.java17.PaymentResult.Approved;
import de.ibikus.appa.hephaestus.modernjava.java17.PaymentResult.Declined;
import de.ibikus.appa.hephaestus.modernjava.java17.PaymentResult.Pending;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class UnnamedExamplesTest {

    @Test
    void countsLinesWithUnnamedLoopVariable() {
        var line = new OrderLine("BRZ", 1, Money.eur("1"));

        assertThat(UnnamedExamples.countLines(List.of(line, line, line))).isEqualTo(3);
    }

    @Test
    void invalidQuantityFallsBackToZero() {
        assertThat(UnnamedExamples.quantityOrZero("12")).isEqualTo(12);
        assertThat(UnnamedExamples.quantityOrZero("twelve")).isZero();
    }

    @Test
    void needsActionIgnoresComponentsItDoesNotUse() {
        assertThat(UnnamedExamples.needsAction(new Declined("no funds"))).isTrue();
        assertThat(UnnamedExamples.needsAction(new Pending(Duration.ofSeconds(5))))
                .isTrue();
        assertThat(UnnamedExamples.needsAction(new Approved("T", Money.eur("1"))))
                .isFalse();
    }
}
