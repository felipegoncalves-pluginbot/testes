package com.felipe.elftemplate.architecture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;

/**
 * Gate de qualidade de testes: impede testes "cosméticos" que só existem para passar CI.
 * Complementa Checkstyle/PMD em {@code config/checkstyle/checkstyle-tests.xml}.
 */
public class MeaningfulUnitTestQualityTest {

  private static final Pattern TEST_ANNOTATION = Pattern.compile("@Test\\b");
  private static final Pattern MEANINGFUL_ASSERT =
      Pattern.compile(
          "\\b(assertEquals|assertNotEquals|assertTrue|assertFalse|assertSame|assertNotSame"
              + "|assertNull|assertNotNull|assertThrows|verify|assertThat)\\s*\\(");

  @Test
  public void unitTestsMustExerciseProductionCodeWithRealAssertions() throws IOException {
    Path testRoot = resolveTestRoot();
    List<String> violations = new ArrayList<>();

    try (java.util.stream.Stream<Path> paths = Files.walk(testRoot)) {
      paths
          .filter(p -> p.toString().endsWith("Test.java"))
          .forEach(p -> checkFile(p, violations));
    }

    if (!violations.isEmpty()) {
      throw new AssertionError(
          "TEST-GUARD: testes insuficientes ou cosméticos detectados:\n"
              + String.join("\n", violations));
    }
  }

  private static void checkFile(Path file, List<String> violations) {
    try {
      String content = new String(Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8);
      String rel = file.toString().replace('\\', '/');

      if (shouldSkipArchitectureGuard(rel, content)) {
        return;
      }
      int testMethods = count(TEST_ANNOTATION, content);
      if (testMethods == 0) {
        return;
      }
      validateTestAssertions(rel, content, testMethods, violations);
    } catch (IOException e) {
      violations.add(file + ": erro ao ler arquivo de teste");
    }
  }

  private static boolean shouldSkipArchitectureGuard(String rel, String content) {
    return (rel.contains("/architecture/") && content.contains("ArchRule"))
        || rel.contains("MeaningfulUnitTestQualityTest")
        || rel.contains("TemporalSignalGuardTest")
        || rel.contains("MemoryBackpressureGuardTest")
        || rel.contains("MirrorMotorGuardTest")
        || rel.contains("MirrorDisplayGuardTest")
        || rel.contains("HeadKinematicsGuardTest")
        || rel.contains("AllocationHotPathGuardTest")
        || rel.contains("CoordinateContractTest");
  }

  private static void validateTestAssertions(
      String rel, String content, int testMethods, List<String> violations) {
    int assertCalls = count(MEANINGFUL_ASSERT, content);
    if (assertCalls < testMethods) {
      violations.add(
          rel + ": cada @Test precisa ≥1 assert/verify (" + assertCalls + " asserts, " + testMethods + " testes)");
    }
    if (!invokesProductionCode(content)) {
      violations.add(rel + ": deve exercitar código em com.felipe.elftemplate.*");
    }
    if (isBehaviorCriticalPackage(rel)) {
      validateBehavioralCritical(rel, content, testMethods, assertCalls, violations);
    }
    if (content.contains("assert" + "True(true)") || content.contains("assert" + "False(false)")) {
      violations.add(rel + ": tautologia assert" + "True(true)/assert" + "False(false)");
    }
  }

  private static void validateBehavioralCritical(
      String rel, String content, int testMethods, int assertCalls, List<String> violations) {
    if (!hasBehavioralSetup(content)) {
      violations.add(
          rel + ": pacote crítico (tracking/logic/movement/server) exige setup de comportamento");
    }
    if (testMethods >= 3 && assertCalls <= testMethods) {
      violations.add(
          rel + ": suite com " + testMethods + " testes precisa mais asserts (" + assertCalls + ")");
    }
  }

  private static boolean isBehaviorCriticalPackage(String rel) {
    return rel.contains("/tracking/")
        || rel.contains("/logic/")
        || rel.contains("/movement/")
        || rel.contains("/server/");
  }

  private static boolean invokesProductionCode(String content) {
    return Pattern.compile("import com\\.felipe\\.elftemplate\\.(?!architecture)")
            .matcher(content)
            .find()
        || (content.contains("com.felipe.elftemplate.") && hasBehavioralSetup(content));
  }

  private static boolean hasBehavioralSetup(String content) {
    return content.contains("for (")
        || content.contains("new short[")
        || content.contains("depthMap")
        || content.contains("processDepthFrame")
        || content.contains(".update")
        || content.contains(".reset(")
        || content.contains("new Kinect")
        || content.contains("new PersonBlob")
        || content.contains("RobotWebServer")
        || content.contains("WheelOdometry");
  }

  private static int count(Pattern pattern, String content) {
    Matcher m = pattern.matcher(content);
    int n = 0;
    while (m.find()) {
      n++;
    }
    return n;
  }

  private static Path resolveTestRoot() {
    Path moduleRoot = Paths.get("src/test/java");
    if (Files.isDirectory(moduleRoot)) {
      return moduleRoot;
    }
    Path projectRoot = Paths.get("app/src/test/java");
    if (Files.isDirectory(projectRoot)) {
      return projectRoot;
    }
    throw new IllegalStateException("Não encontrou src/test/java");
  }
}
