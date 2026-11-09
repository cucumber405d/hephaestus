package de.ibikus.appa.hephaestus.restservice.catalog;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/** Hexagonal architecture rules. Empty layers are allowed while the service is being built up. */
class HexagonalArchitectureTest {

    private static final String BASE = CatalogApplication.class.getPackageName();

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE);

    @Test
    void layersDependInward() {
        layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .optionalLayer("Domain")
                .definedBy(BASE + ".domain..")
                .optionalLayer("Application")
                .definedBy(BASE + ".application..")
                .optionalLayer("AdapterIn")
                .definedBy(BASE + ".adapter.in..")
                .optionalLayer("AdapterOut")
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
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void generatedApiIsUsedOnlyByTheWebAdapter() {
        noClasses()
                .that()
                .resideOutsideOfPackage(BASE + ".adapter.in.web..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage(BASE + ".adapter.in.web.api..")
                .allowEmptyShould(true)
                .check(classes);
    }
}
