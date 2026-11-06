package de.ibikus.appa.hephaestus.template.greeting;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/** Гексагональные правила. Обычный JUnit-тест: не зависит от ArchUnit test engine. */
class HexagonalArchitectureTest {

    /** Из класса, а не строкой: переживает переименование пакета при копировании шаблона. */
    private static final String BASE = GreetingApplication.class.getPackageName();

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
                .check(classes);
    }
}
