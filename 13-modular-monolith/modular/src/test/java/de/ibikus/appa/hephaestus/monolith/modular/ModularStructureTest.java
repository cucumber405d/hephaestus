package de.ibikus.appa.hephaestus.monolith.modular;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * The "after" picture: the same functionality as the legacy, and Spring Modulith has nothing to complain about.
 * Writes the module diagrams to 13-modular-monolith/docs/modules (committed).
 */
class ModularStructureTest {

    static final ApplicationModules MODULES = ApplicationModules.of(ModularApplication.class);

    @Test
    void verifyPasses() {
        MODULES.verify();
    }

    @Test
    void writesTheModuleDocumentation() {
        new Documenter(MODULES, Documenter.Options.defaults().withOutputFolder("../docs/modules")).writeDocumentation();
    }
}
