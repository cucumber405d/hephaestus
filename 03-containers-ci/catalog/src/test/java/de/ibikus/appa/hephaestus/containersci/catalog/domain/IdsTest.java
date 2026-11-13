package de.ibikus.appa.hephaestus.containersci.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IdsTest {

    @Test
    void generatesVersion7Rfc9562Uuids() {
        var id = Ids.uuidV7();

        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    void laterIdsSortAfterEarlierOnes() throws InterruptedException {
        var first = Ids.uuidV7();
        Thread.sleep(2);
        var second = Ids.uuidV7();

        // UUID.compareTo compares signed longs; the time prefix is compared as unsigned text instead
        assertThat(first.toString()).isLessThan(second.toString());
    }

    @Test
    void embedsTheCurrentUnixMillis() {
        long before = System.currentTimeMillis();
        long embedded = Ids.uuidV7().getMostSignificantBits() >>> 16;
        long after = System.currentTimeMillis();

        assertThat(embedded).isBetween(before, after);
    }
}
