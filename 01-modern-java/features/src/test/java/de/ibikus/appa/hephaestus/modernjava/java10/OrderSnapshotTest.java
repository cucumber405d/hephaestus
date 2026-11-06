package de.ibikus.appa.hephaestus.modernjava.java10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderSnapshotTest {

    @Test
    void snapshotIsDetachedFromSource() {
        var source = new ArrayList<>(List.of("BRZ", "IRN"));

        var snapshot = OrderSnapshot.of(source);
        source.add("STL");

        assertThat(snapshot.items()).containsExactly("BRZ", "IRN");
    }

    @Test
    void java10CollectorsAndOptional() {
        var codes = java.util.stream.Stream.of("BRZ", "IRN").collect(java.util.stream.Collectors.toUnmodifiableList());

        assertThatThrownBy(() -> codes.add("X")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> java.util.Optional.empty().orElseThrow())
                .isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    void snapshotIsUnmodifiable() {
        var snapshot = OrderSnapshot.of(List.of("BRZ"));

        assertThatThrownBy(() -> snapshot.items().add("X")).isInstanceOf(UnsupportedOperationException.class);
    }
}
