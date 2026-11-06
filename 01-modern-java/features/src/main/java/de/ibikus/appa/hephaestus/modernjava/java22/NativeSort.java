package de.ibikus.appa.hephaestus.modernjava.java22;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.Consumer;

/**
 * FFM upcalls: C {@code qsort} calls back into a Java comparator. While the comparator runs, a native frame sits
 * on the stack, so a virtual thread that blocks here stays pinned to its carrier (see java24 PinningTest).
 */
public final class NativeSort {

    private static final Linker LINKER = Linker.nativeLinker();

    private static final MethodHandle QSORT = LINKER.downcallHandle(
            LINKER.defaultLookup().find("qsort").orElseThrow(),
            FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG, JAVA_LONG, ADDRESS));

    private static final FunctionDescriptor COMPARATOR =
            FunctionDescriptor.of(JAVA_INT, ADDRESS.withTargetLayout(JAVA_INT), ADDRESS.withTargetLayout(JAVA_INT));

    private static final MethodHandle COMPARE_DESCENDING;

    static {
        try {
            COMPARE_DESCENDING = MethodHandles.lookup()
                    .findStatic(
                            NativeSort.class,
                            "compareDescending",
                            MethodType.methodType(int.class, Consumer.class, MemorySegment.class, MemorySegment.class));
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private NativeSort() {}

    /**
     * Sorts a copy of {@code values} in descending order with native {@code qsort}. {@code onCompare} receives each
     * comparison result inside the upcall; it must not throw, or the JVM terminates.
     */
    public static int[] sortDescending(int[] values, Consumer<Integer> onCompare) throws Throwable {
        try (var arena = Arena.ofConfined()) {
            var array = arena.allocateFrom(JAVA_INT, values);
            var comparator = LINKER.upcallStub(COMPARE_DESCENDING.bindTo(onCompare), COMPARATOR, arena);
            QSORT.invokeExact(array, (long) values.length, JAVA_INT.byteSize(), comparator);
            return array.toArray(JAVA_INT);
        }
    }

    private static int compareDescending(Consumer<Integer> onCompare, MemorySegment left, MemorySegment right) {
        int result = Integer.compare(right.get(JAVA_INT, 0), left.get(JAVA_INT, 0));
        onCompare.accept(result);
        return result;
    }
}
