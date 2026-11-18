package de.ibikus.appa.hephaestus.resilience.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class StockItemTest {

    @Test
    void reserveIncreasesReserved() {
        var item = new StockItem("ANVIL-01", "Anvil", 10, 2).reserve(3);

        assertThat(item.reserved()).isEqualTo(5);
        assertThat(item.available()).isEqualTo(5);
    }

    @Test
    void reservingMoreThanAvailableFails() {
        var item = new StockItem("SWORD-01", "Bronze sword", 2, 1);

        assertThatThrownBy(() -> item.reserve(2))
                .isInstanceOf(InsufficientStockException.class)
                .satisfies(ex -> {
                    var insufficient = (InsufficientStockException) ex;
                    assertThat(insufficient.sku()).isEqualTo("SWORD-01");
                    assertThat(insufficient.available()).isEqualTo(1);
                });
    }

    @Test
    void rejectsInconsistentCounts() {
        assertThatThrownBy(() -> new StockItem("X-1", "X", 1, 2)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new StockItem("lower", "X", 1, 0)).isInstanceOf(InvalidValueException.class);
    }

    @Test
    void reservationLineLimitsQuantity() {
        assertThatThrownBy(() -> new ReservationLine("ANVIL-01", 0)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new ReservationLine("ANVIL-01", 1001)).isInstanceOf(InvalidValueException.class);
    }
}
