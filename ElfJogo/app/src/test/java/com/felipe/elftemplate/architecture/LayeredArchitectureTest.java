package com.felipe.elftemplate.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.Before;
import org.junit.Test;

/** Testes de integridade arquitetural em camadas e prevenção de acoplamento indevido. */
public class LayeredArchitectureTest {

  private JavaClasses importedClasses;

  @Before
  public void setUp() {
    importedClasses =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.felipe.elftemplate");
  }

  @Test
  public void logicLayerShouldNotDependOnWebServer() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("..logic..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..server..");

    rule.check(importedClasses);
  }

  @Test
  public void serverLayerShouldNotDirectlyAccessTrackingAlgorithms() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("..server..")
            .should()
            .dependOnClassesThat()
            .haveSimpleNameEndingWith("KinectTrackingEngine");

    rule.check(importedClasses);
  }

  @Test
  public void packageSlicesShouldBeFreeOfCycles() {
    ArchRule rule = slices().matching("com.felipe.elftemplate.(*)..").should().beFreeOfCycles();

    rule.check(importedClasses);
  }
}
