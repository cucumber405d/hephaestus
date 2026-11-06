package de.ibikus.appa.hephaestus.modernjava.java14;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OrderWorkflowTest {

    @Test
    void movesOrderForward() {
        assertThat(OrderWorkflow.next(OrderStatus.NEW)).isEqualTo(OrderStatus.PAID);
        assertThat(OrderWorkflow.next(OrderStatus.PAID)).isEqualTo(OrderStatus.IN_FORGE);
        assertThat(OrderWorkflow.next(OrderStatus.IN_FORGE)).isEqualTo(OrderStatus.SHIPPED);
    }

    @Test
    void finalStatesHaveNoNextStep() {
        assertThatThrownBy(() -> OrderWorkflow.next(OrderStatus.SHIPPED)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> OrderWorkflow.next(OrderStatus.CANCELLED)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void labelUsesYieldInsideABlock() {
        assertThat(OrderWorkflow.label(OrderStatus.IN_FORGE)).isEqualTo("in forge (being made)");
        assertThat(OrderWorkflow.label(OrderStatus.NEW)).isEqualTo("new");
    }

    @Test
    void helpfulNullPointerExceptionNamesTheNullExpression() {
        OrderStatus[] statuses = {null};

        assertThatThrownBy(() -> statuses[0].name())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("\"statuses[0]\" is null");
    }
}
