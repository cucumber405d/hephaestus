package de.ibikus.appa.hephaestus.restservice.catalog.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.ibikus.appa.hephaestus.restservice.catalog.domain.VersionConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ETagsTest {

    @Test
    void formatsAndParsesStrongETags() {
        assertThat(ETags.of(3)).isEqualTo("\"3\"");
        assertThat(ETags.parseIfMatch("\"3\"")).isEqualTo(3);
        assertThat(ETags.parseIfMatch(" \"12\" ")).isEqualTo(12);
    }

    @ParameterizedTest
    @ValueSource(strings = {"*", "W/\"0\"", "\"abc\"", "\"1\", \"2\"", "3", "\"\"", "\"-1\""})
    void anythingButOneStrongNumericETagIsAPreconditionFailure(String header) {
        assertThatThrownBy(() -> ETags.parseIfMatch(header)).isInstanceOf(VersionConflictException.class);
    }
}
