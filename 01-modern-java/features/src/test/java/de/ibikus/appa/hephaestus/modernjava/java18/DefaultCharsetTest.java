package de.ibikus.appa.hephaestus.modernjava.java18;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Learning test: JEP 400 made UTF-8 the default charset on every OS, including Windows. */
class DefaultCharsetTest {

    @Test
    void defaultCharsetIsUtf8() {
        assertThat(Charset.defaultCharset()).isEqualTo(StandardCharsets.UTF_8);
    }
}
