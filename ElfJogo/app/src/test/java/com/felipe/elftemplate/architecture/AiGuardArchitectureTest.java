package com.felipe.elftemplate.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.Before;
import org.junit.Test;

/** ArchUnit AI-GUARD: threading, lifecycle Android e camadas hexagonais. */
public class AiGuardArchitectureTest {

  private JavaClasses productionClasses;

  @Before
  public void setUp() {
    productionClasses =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.felipe.elftemplate");
  }

  @Test
  public void productionCodeShouldNotUseDeprecatedAsyncTask() {
    ArchRule rule =
        noClasses()
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("android.os.AsyncTask");

    rule.check(productionClasses);
  }

  @Test
  public void logicLayerShouldNotDependOnSanbotSdk() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("..logic..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.sanbot.opensdk..");

    rule.check(productionClasses);
  }

  @Test
  public void logicLayerShouldNotDependOnServerLayer() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("..logic..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..server..");

    rule.check(productionClasses);
  }

  @Test
  public void productionCodeShouldNotContainMockOrDummyClasses() {
    ArchRule rule =
        noClasses()
            .should()
            .haveSimpleNameContaining("Mock")
            .orShould()
            .haveSimpleNameContaining("Dummy");

    rule.check(productionClasses);
  }

  @Test
  public void activitiesShouldExtendAndroidActivity() {
    ArchRule rule =
        classes()
            .that()
            .haveSimpleNameEndingWith("Activity")
            .should()
            .beAssignableTo(android.app.Activity.class);

    rule.check(productionClasses);
  }
}
