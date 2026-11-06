package de.ibikus.appa.hephaestus.modernjava.java22;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NativeStringsTest {

    @Test
    void callsStrlenFromTheCRuntime() throws Throwable {
        assertThat(NativeStrings.strlen("Hephaestus")).isEqualTo(10);
    }

    @Test
    void emptyStringHasLengthZero() throws Throwable {
        assertThat(NativeStrings.strlen("")).isZero();
    }
}
