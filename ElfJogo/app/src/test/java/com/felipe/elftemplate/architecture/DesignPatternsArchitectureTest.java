package com.felipe.elftemplate.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import android.app.Activity;
import androidx.appcompat.app.AppCompatActivity;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.Before;
import org.junit.Test;

/** Testes arquiteturais para validação de padrões de projeto e convenções de nomenclatura. */
public class DesignPatternsArchitectureTest {

  private JavaClasses importedClasses;

  @Before
  public void setUp() {
    importedClasses =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.felipe.elftemplate");
  }

  @Test
  public void activitiesShouldInheritFromActivityOrAppCompatActivity() {
    ArchRule rule =
        classes()
            .that()
            .haveSimpleNameEndingWith("Activity")
            .should()
            .beAssignableTo(Activity.class)
            .orShould()
            .beAssignableTo(AppCompatActivity.class);

    rule.check(importedClasses);
  }

  @Test
  public void controllersAndEnginesShouldBePublic() {
    ArchRule rule =
        classes()
            .that()
            .haveSimpleNameEndingWith("Engine")
            .or()
            .haveSimpleNameEndingWith("Controller")
            .should()
            .bePublic();

    rule.check(importedClasses);
  }

  @Test
  public void exceptionsShouldInheritFromException() {
    ArchRule rule =
        classes()
            .that()
            .haveSimpleNameEndingWith("Exception")
            .should()
            .beAssignableTo(Exception.class);

    rule.check(importedClasses);
  }
}
