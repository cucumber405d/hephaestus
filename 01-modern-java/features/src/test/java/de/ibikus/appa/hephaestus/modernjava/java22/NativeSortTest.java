package de.ibikus.appa.hephaestus.modernjava.java22;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class NativeSortTest {

    @Test
    void sortsDescendingWithNativeQsortAndJavaComparatorUpcall() throws Throwable {
        var comparisons = new AtomicInteger();

        int[] sorted = NativeSort.sortDescending(new int[] {3, 1, 2}, comparison -> comparisons.incrementAndGet());

        assertThat(sorted).containsExactly(3, 2, 1);
        assertThat(comparisons.get()).isPositive();
    }

    @Test
    void leavesInputArrayUntouched() throws Throwable {
        int[] input = {1, 2, 3};

        NativeSort.sortDescending(input, comparison -> {});

        assertThat(input).containsExactly(1, 2, 3);
    }
}
