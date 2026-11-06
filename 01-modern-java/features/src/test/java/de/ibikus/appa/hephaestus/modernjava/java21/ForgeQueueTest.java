package de.ibikus.appa.hephaestus.modernjava.java21;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ForgeQueueTest {

    @Test
    void keepsInsertionOrderWithFirstLastAndReversed() {
        var queue = new ForgeQueue();
        queue.add(new WorkItem("a", "sword"));
        queue.add(new WorkItem("b", "shield"));
        queue.add(new WorkItem("c", "helmet"));

        assertThat(queue.first().id()).isEqualTo("a");
        assertThat(queue.last().id()).isEqualTo("c");
        assertThat(queue.idsNewestFirst()).containsExactly("c", "b", "a");
    }

    @Test
    void urgentItemJumpsToFront() {
        var queue = new ForgeQueue();
        queue.add(new WorkItem("a", "sword"));
        queue.addUrgent(new WorkItem("z", "horseshoe"));

        assertThat(queue.first().id()).isEqualTo("z");
    }

    @Test
    void java21SmallApiAdditions() {
        assertThat(Math.clamp(150, 0, 100)).isEqualTo(100);
        assertThat("=".repeat(3)).isEqualTo("===");
        assertThat(new StringBuilder().repeat("=", 3).toString()).isEqualTo("===");
    }
}
