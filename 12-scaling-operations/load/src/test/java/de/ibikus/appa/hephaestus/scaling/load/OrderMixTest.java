package de.ibikus.appa.hephaestus.scaling.load;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class OrderMixTest {

    @Test
    void mixIsEightyTenTen() {
        Map<String, Integer> expected = new HashMap<>();
        LongStream.range(0, 1000).mapToObj(OrderMix::of).forEach(o -> expected.merge(o.expected(), 1, Integer::sum));

        assertThat(expected)
                .containsEntry("CONFIRMED", 800)
                .containsEntry("REJECTED:credit limit exceeded", 100)
                .containsEntry("REJECTED:not enough stock", 100);
    }

    @Test
    void bothSagaStylesForEveryKindOfOrder() {
        Map<String, Integer> styles = new HashMap<>();
        LongStream.range(0, 1000)
                .mapToObj(OrderMix::of)
                .forEach(o -> styles.merge(o.expected() + "/" + o.style(), 1, Integer::sum));

        assertThat(styles)
                .containsEntry("CONFIRMED/choreography", 400)
                .containsEntry("CONFIRMED/orchestration", 400)
                .containsEntry("REJECTED:credit limit exceeded/choreography", 50)
                .containsEntry("REJECTED:credit limit exceeded/orchestration", 50)
                .containsEntry("REJECTED:not enough stock/choreography", 50)
                .containsEntry("REJECTED:not enough stock/orchestration", 50);
    }

    @Test
    void successUsesFreshCustomersWithinTheDefaultLimit() {
        LongStream.range(0, 1000).mapToObj(OrderMix::of).filter(o -> o.expected().equals("CONFIRMED")).forEach(o -> {
            assertThat(o.customer()).startsWith("peak-");
            assertThat(o.sku()).isIn("HAMMER-01", "ANVIL-01");
        });
    }

    @Test
    void sameIndexSameOrder() {
        assertThat(OrderMix.of(42)).isEqualTo(OrderMix.of(42));
    }
}
