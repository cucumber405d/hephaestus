package de.ibikus.appa.hephaestus.template.greeting;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/** Гексагональные правила. Обычный JUnit-тест: не зависит от ArchUnit test engine. */
class HexagonalArchitectureTest {

    private static final String BASE = "de.ibikus.appa.hephaestus.template.greeting";

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE);

    @Test
    void layersDependInward() {
        layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer("Domain")
                .definedBy(BASE + ".domain..")
                .layer("Application")
                .definedBy(BASE + ".application..")
                .layer("Adapter")
                .definedBy(BASE + ".adapter..")
                .whereLayer("Adapter")
                .mayNotBeAccessedByAnyLayer()
                .whereLayer("Application")
                .mayOnlyBeAccessedByLayers("Adapter")
                .whereLayer("Domain")
                .mayOnlyBeAccessedByLayers("Application", "Adapter")
                .check(classes);
    }

    @Test
    void domainIsFrameworkFree() {
        noClasses()
                .that()
                .resideInAPackage(BASE + ".domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("org.springframework..", "jakarta..")
                .check(classes);
    }
}
