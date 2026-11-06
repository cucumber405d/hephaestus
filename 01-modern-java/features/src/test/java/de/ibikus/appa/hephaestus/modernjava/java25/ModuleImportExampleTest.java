package de.ibikus.appa.hephaestus.modernjava.java25;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ModuleImportExampleTest {

    @Test
    void sortsCatalogCodes() {
        assertThat(ModuleImportExample.sortedCodes(Map.of("STL", 1, "BRZ", 2, "IRN", 3)))
                .containsExactly("BRZ", "IRN", "STL");
    }
}
