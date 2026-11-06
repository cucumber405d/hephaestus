package de.ibikus.appa.hephaestus.modernjava.java22;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * Java 22 Foreign Function and Memory API (JEP 454; preview in 19-21): a downcall into the C runtime without JNI,
 * no C code and no build step. The replacement for JNI.
 */
public final class NativeStrings {

    private static final Linker LINKER = Linker.nativeLinker();

    private static final MethodHandle STRLEN = LINKER.downcallHandle(
            LINKER.defaultLookup().find("strlen").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

    private NativeStrings() {}

    /** {@code size_t strlen(const char*)}; the Java string is copied into a NUL-terminated native buffer. */
    public static long strlen(String text) throws Throwable {
        try (var arena = Arena.ofConfined()) {
            var cString = arena.allocateFrom(text);
            return (long) STRLEN.invokeExact(cString);
        }
    }
}
