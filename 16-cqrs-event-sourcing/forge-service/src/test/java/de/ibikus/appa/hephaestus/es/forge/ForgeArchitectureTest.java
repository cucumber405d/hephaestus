package de.ibikus.appa.hephaestus.es.forge;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/** The extracted service is hexagonal, like 02: what Modulith checked inside the monolith, ArchUnit checks here. */
class ForgeArchitectureTest {

    private static final String BASE = ForgeServiceApplication.class.getPackageName();

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
                .layer("AdapterIn")
                .definedBy(BASE + ".adapter.in..")
                .layer("AdapterOut")
                .definedBy(BASE + ".adapter.out..")
                .whereLayer("AdapterIn")
                .mayNotBeAccessedByAnyLayer()
                .whereLayer("AdapterOut")
                .mayNotBeAccessedByAnyLayer()
                .whereLayer("Application")
                .mayOnlyBeAccessedByLayers("AdapterIn", "AdapterOut")
                .whereLayer("Domain")
                .mayOnlyBeAccessedByLayers("Application", "AdapterIn", "AdapterOut")
                .check(classes);
    }

    @Test
    void domainDependsOnlyOnJdk() {
        classes()
                .that()
                .resideInAPackage(BASE + ".domain..")
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage(BASE + ".domain..", "java..")
                .check(classes);
    }
}
