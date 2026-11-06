package de.ibikus.appa.hephaestus.modernjava.java24;

import static org.assertj.core.api.Assertions.assertThat;

import de.ibikus.appa.hephaestus.modernjava.java16.Money;
import de.ibikus.appa.hephaestus.modernjava.java16.OrderLine;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Gatherers;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class PriceGatherersTest {

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void movingAverageOverSlidingWindow() {
        var prices = List.of(new BigDecimal("10"), new BigDecimal("20"), new BigDecimal("30"), new BigDecimal("40"));

        assertThat(PriceGatherers.movingAverage(prices, 2))
                .containsExactly(new BigDecimal("15.00"), new BigDecimal("25.00"), new BigDecimal("35.00"));
    }

    @Test
    void windowLargerThanInputYieldsOneAverage() {
        assertThat(PriceGatherers.movingAverage(List.of(new BigDecimal("10")), 3))
                .containsExactly(new BigDecimal("10.00"));
    }

    @Test
    void customDistinctByKeepsFirstLinePerMaterial() {
        var first = new OrderLine("BRZ", 1, Money.eur("1"));
        var second = new OrderLine("IRN", 2, Money.eur("1"));
        var duplicate = new OrderLine("BRZ", 9, Money.eur("9"));
        var afterDuplicate = new OrderLine("STL", 1, Money.eur("1"));

        assertThat(Stream.of(first, second, duplicate, afterDuplicate)
                        .gather(PriceGatherers.distinctBy(OrderLine::materialCode))
                        .toList())
                .containsExactly(first, second, afterDuplicate);
    }

    @Test
    void builtInGatherers() {
        assertThat(Stream.of(1, 2, 3, 4, 5, 6, 7)
                        .gather(Gatherers.windowFixed(3))
                        .toList())
                .containsExactly(List.of(1, 2, 3), List.of(4, 5, 6), List.of(7));
        assertThat(Stream.of(1, 2, 3)
                        .gather(Gatherers.fold(() -> 0, Integer::sum))
                        .toList())
                .containsExactly(6);
        assertThat(Stream.of(1, 2, 3)
                        .gather(Gatherers.scan(() -> 0, Integer::sum))
                        .toList())
                .containsExactly(1, 3, 6);
        assertThat(Stream.of(1, 2, 3, 4)
                        // the first element finishes last, yet the output keeps encounter order
                        .gather(Gatherers.mapConcurrent(4, x -> {
                            sleepQuietly(x == 1 ? 200 : 10);
                            return x * 2;
                        }))
                        .toList())
                .containsExactly(2, 4, 6, 8);
    }
}
