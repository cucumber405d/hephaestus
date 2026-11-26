package de.ibikus.appa.hephaestus.es.ordering;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/** Ordering's modules (ordering, inventory) still pass Spring Modulith's verification; diagrams to docs/modules. */
class OrderingStructureTest {

    static final ApplicationModules MODULES = ApplicationModules.of(OrderingApplication.class);

    @Test
    void verifyPasses() {
        MODULES.verify();
    }

    @Test
    void writesTheModuleDocumentation() {
        new Documenter(MODULES, Documenter.Options.defaults().withOutputFolder("../docs/modules")).writeDocumentation();
    }
}
