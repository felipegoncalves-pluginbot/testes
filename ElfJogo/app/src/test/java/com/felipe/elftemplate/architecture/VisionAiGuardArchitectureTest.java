package com.felipe.elftemplate.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.Before;
import org.junit.Test;

/**
 * ArchUnit AI-GUARD: robótica, visão computacional, Orbbec/OpenNI e fusão ML Kit+depth.
 * Fontes: Orbbec forums, OpenNI2 Android issues, ML Kit spatial mapping, embodied AI frame docs.
 */
public class VisionAiGuardArchitectureTest {

  private JavaClasses productionClasses;

  @Before
  public void setUp() {
    productionClasses =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.felipe.elftemplate");
  }

  @Test
  public void trackingShouldNotDependOnMovementLayer() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("..tracking..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..movement..");

    rule.check(productionClasses);
  }

  @Test
  public void trackingShouldNotDependOnLogicLayer() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("..tracking..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..logic..");

    rule.check(productionClasses);
  }

  @Test
  public void logicShouldNotDependOnOpenNiOrOrbbecNative() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("..logic..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("org.openni..")
            .orShould()
            .dependOnClassesThat()
            .resideInAPackage("org.openni.android..");

    rule.check(productionClasses);
  }

  @Test
  public void serverShouldNotDirectlyAccessKinectTrackingEngine() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("..server..")
            .should()
            .dependOnClassesThat()
            .haveSimpleNameEndingWith("KinectTrackingEngine");

    rule.check(productionClasses);
  }
}
