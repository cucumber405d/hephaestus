package de.ibikus.appa.hephaestus.modernjava.java27;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SupplierAggregatorTest {

    @Test
    void combinesThreeConcurrentAnswers() throws Exception {
        var offer = SupplierAggregator.offer(() -> new BigDecimal("4.80"), () -> 12, () -> Duration.ofDays(3));

        assertThat(offer).isEqualTo(new SupplierOffer(new BigDecimal("4.80"), 12, Duration.ofDays(3)));
    }

    @Test
    void oneFailureCancelsTheSiblingsAndPropagates() {
        var completed = new AtomicInteger();
        long start = System.nanoTime();

        assertThatThrownBy(() -> SupplierAggregator.offer(
                        () -> {
                            Thread.sleep(2_000);
                            completed.incrementAndGet();
                            return BigDecimal.ONE;
                        },
                        () -> {
                            throw new IOException("stock service down");
                        },
                        () -> {
                            Thread.sleep(2_000);
                            completed.incrementAndGet();
                            return Duration.ZERO;
                        }))
                .hasRootCauseInstanceOf(IOException.class)
                .hasRootCauseMessage("stock service down");

        assertThat(completed.get()).as("siblings were cancelled").isZero();
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));
    }
}
