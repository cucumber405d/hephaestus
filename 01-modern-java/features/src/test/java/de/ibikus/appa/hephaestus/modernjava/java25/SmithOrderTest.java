package de.ibikus.appa.hephaestus.modernjava.java25;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SmithOrderTest {

    @Test
    void invalidQuantityIsRejectedBeforeTheSuperclassRuns() {
        int before = WorkOrder.CREATED.get();

        assertThatThrownBy(() -> new SmithOrder(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Quantity must be positive: 0");
        assertThat(WorkOrder.CREATED.get()).isEqualTo(before);
    }

    @Test
    void validOrderIsCreated() {
        int before = WorkOrder.CREATED.get();

        assertThat(new SmithOrder(2).quantity()).isEqualTo(2);
        assertThat(WorkOrder.CREATED.get()).isEqualTo(before + 1);
    }
}
