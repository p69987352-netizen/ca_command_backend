package com.caCommand.caCommand.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

public class ArchitectureTest {

    private static JavaClasses importedClasses;

    @BeforeAll
    public static void setUp() {
        importedClasses = new ClassFileImporter().importPackages("com.caCommand.caCommand");
    }

    @Test
    public void servicesShouldNotDependOnWebLayer() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..services..")
                .should().dependOnClassesThat().resideInAPackage("..controllers..");
        rule.check(importedClasses);
    }

    @Test
    public void controllersShouldBeSuffixed() {
        ArchRule rule = classes()
                .that().resideInAPackage("..controllers..")
                .should().haveSimpleNameEndingWith("Controller");
        rule.check(importedClasses);
    }
}
