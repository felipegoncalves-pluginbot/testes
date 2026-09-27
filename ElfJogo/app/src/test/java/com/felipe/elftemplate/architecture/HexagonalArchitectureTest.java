package com.felipe.elftemplate.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.Before;
import org.junit.Test;

/** Testes arquiteturais inspirados em regras de fronteira e segurança (Hexagonal Boundaries). */
public class HexagonalArchitectureTest {

  private JavaClasses importedProductionClasses;

  @Before
  public void setUp() {
    importedProductionClasses =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.felipe.elftemplate");
  }

  @Test
  public void logicDomainShouldNotDependOnExternalAdaptersOrServer() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("..logic..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..server..")
            .orShould()
            .dependOnClassesThat()
            .resideInAPackage("com.sanbot.opensdk..");

    rule.check(importedProductionClasses);
  }

  @Test
  public void productionCodeShouldNotContainMockOrDummyClasses() {
    ArchRule rule =
        noClasses()
            .should()
            .haveSimpleNameContaining("Mock")
            .orShould()
            .haveSimpleNameContaining("Dummy");

    rule.check(importedProductionClasses);
  }
}
