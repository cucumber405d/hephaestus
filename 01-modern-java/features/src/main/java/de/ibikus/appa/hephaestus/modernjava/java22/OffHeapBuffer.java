package de.ibikus.appa.hephaestus.modernjava.java22;

import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.Arena;

/**
 * FFM memory access: an off-heap segment with deterministic release (the arena closes it), bounds checks and
 * no {@code sun.misc.Unsafe}.
 */
public final class OffHeapBuffer {

    private OffHeapBuffer() {}

    public static long[] roundTrip(long[] values) {
        try (var arena = Arena.ofConfined()) {
            var segment = arena.allocate(JAVA_LONG, values.length);
            for (int i = 0; i < values.length; i++) {
                segment.setAtIndex(JAVA_LONG, i, values[i]);
            }
            var result = new long[values.length];
            for (int i = 0; i < values.length; i++) {
                result[i] = segment.getAtIndex(JAVA_LONG, i);
            }
            return result;
        }
    }
}
