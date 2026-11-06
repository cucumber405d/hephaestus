package de.ibikus.appa.hephaestus.modernjava.java09;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Learning tests: documents Java 9 Stream and Optional additions. */
class StreamAndOptionalTest {

    @Test
    void takeWhileStopsAtFirstMismatch() {
        assertThat(Stream.of(1, 3, 5, 2).takeWhile(i -> i < 5).toList()).containsExactly(1, 3);
    }

    @Test
    void dropWhileSkipsLeadingMatches() {
        assertThat(Stream.of(1, 3, 5, 2).dropWhile(i -> i < 5).toList()).containsExactly(5, 2);
    }

    @Test
    void iterateWithPredicate() {
        assertThat(Stream.iterate(1, i -> i <= 100, i -> i * 2).toList()).isEqualTo(List.of(1, 2, 4, 8, 16, 32, 64));
    }

    @Test
    void optionalOrSuppliesAlternative() {
        assertThat(Optional.<String>empty().or(() -> Optional.of("IRN")).orElseThrow())
                .isEqualTo("IRN");
    }
}
