package com.felipe.elftemplate.architecture;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.Test;

/**
 * ALLOC-GUARD: new Joint/PoseLandmarkData/TrackingResult/PoseFrame só em field-init ou factory.
 */
public class AllocationHotPathGuardTest {

  private static final Set<String> EXEMPT_FILES =
      new HashSet<>(
          Arrays.asList(
              "TrackingResult.java",
              "Joint.java",
              "PoseLandmarkData.java",
              "PoseFrame.java"));

  private static final Pattern NEW_HOT =
      Pattern.compile("\\bnew\\s+(Joint|PoseLandmarkData|PoseFrame|TrackingResult)\\s*\\(");

  private static final Pattern FIELD_INIT =
      Pattern.compile(
          "^(public|private|protected)\\b.*\\bnew\\s+(Joint|PoseLandmarkData|PoseFrame|TrackingResult)\\s*\\(");

  @Test
  public void trackingHotPathMustNotAllocateJointsOrFrames() throws IOException {
    Path root = resolveTrackingDir();
    List<String> violations = new ArrayList<>();
    try (Stream<Path> paths = Files.walk(root)) {
      paths.filter(p -> p.toString().endsWith(".java")).forEach(p -> checkFile(p, violations));
    }
    if (!violations.isEmpty()) {
      throw new AssertionError(
          "ALLOC-GUARD: alocação no hot path de visão:\n" + String.join("\n", violations));
    }
  }

  private static void checkFile(Path file, List<String> violations) {
    String name = file.getFileName().toString();
    if (EXEMPT_FILES.contains(name)) {
      return;
    }
    try {
      List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
      String rel = file.toString().replace('\\', '/');
      for (int i = 0; i < lines.size(); i++) {
        String trimmed = stripComment(lines.get(i));
        if (trimmed.isEmpty() || !NEW_HOT.matcher(trimmed).find()) {
          continue;
        }
        if (FIELD_INIT.matcher(trimmed).find()) {
          continue;
        }
        violations.add(rel + ":" + (i + 1) + " " + trimmed.trim());
      }
    } catch (IOException e) {
      violations.add(file + ": erro ao ler");
    }
  }

  private static String stripComment(String line) {
    int idx = line.indexOf("//");
    return idx >= 0 ? line.substring(0, idx).trim() : line.trim();
  }

  private static Path resolveTrackingDir() {
    Path direct = Paths.get("src/main/java/com/felipe/elftemplate/tracking");
    if (Files.isDirectory(direct)) {
      return direct;
    }
    return Paths.get("app/src/main/java/com/felipe/elftemplate/tracking");
  }
}
