package de.ibikus.appa.hephaestus.modernjava.java22;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OffHeapBufferTest {

    @Test
    void writesAndReadsLongsOutsideTheHeap() {
        assertThat(OffHeapBuffer.roundTrip(new long[] {1, Long.MAX_VALUE, -7})).containsExactly(1, Long.MAX_VALUE, -7);
    }
}
