package de.ibikus.appa.hephaestus.monolith.legacy;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * What Spring Modulith thinks of the legacy: the build stays green, but the violations are pinned. If someone
 * "fixes" the legacy by accident, this test fails — the legacy is the "before" picture of 13.
 */
class LegacyStructureTest {

    @Test
    void verifyFailsWithTheKnownViolations() {
        var modules = ApplicationModules.of(LegacyApplication.class);
        assertThatThrownBy(modules::verify)
                .hasMessageContaining("Cycle detected")
                .hasMessageContaining("ordering")
                .hasMessageContaining("forge");
    }
}
