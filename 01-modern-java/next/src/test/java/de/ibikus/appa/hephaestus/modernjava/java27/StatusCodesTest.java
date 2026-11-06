package de.ibikus.appa.hephaestus.modernjava.java27;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StatusCodesTest {

    @Test
    void describesStatusCodesWithPrimitivePatterns() {
        assertThat(StatusCodes.describe(200)).isEqualTo("ok");
        assertThat(StatusCodes.describe(404)).isEqualTo("not found");
        assertThat(StatusCodes.describe(503)).isEqualTo("server error");
        assertThat(StatusCodes.describe(302)).isEqualTo("other");
    }
}
