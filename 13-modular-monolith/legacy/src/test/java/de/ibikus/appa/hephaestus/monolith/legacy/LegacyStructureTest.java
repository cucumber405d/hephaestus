package de.ibikus.appa.hephaestus.monolith.legacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

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
        var violations = catchThrowable(modules::verify);
        // The list is the story artifact (legacy/README.md): print it into the build log
        System.out.println("Spring Modulith on the legacy:%n%s".formatted(violations.getMessage()));
        assertThat(violations)
                .hasMessageContaining("Cycle detected")
                .hasMessageContaining("ordering")
                .hasMessageContaining("forge");
    }
}
