package de.ibikus.appa.hephaestus.modernjava.java27;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TariffTableTest {

    @Test
    void loadsTariffsOnceOnFirstAccess() {
        var loads = new AtomicInteger();
        var table = new TariffTable(() -> {
            loads.incrementAndGet();
            return Map.of("express", new BigDecimal("15.00"));
        });

        assertThat(loads.get()).as("nothing loaded before first access").isZero();
        assertThat(table.get()).containsEntry("express", new BigDecimal("15.00"));
        assertThat(table.get()).isSameAs(table.get());
        assertThat(loads.get()).isEqualTo(1);
    }
}
