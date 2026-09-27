package com.felipe.elftemplate.architecture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.Test;

/**
 * FSM-GUARD: impede heurísticas sem estado em tracking/logic (if dist &lt; 1000 sem histerese/EMA).
 * Complementa {@code checkstyleTemporal} e {@code pmdTemporal}.
 */
public class TemporalSignalGuardTest {

  private static final Set<String> EXEMPT_FILES =
      new HashSet<>(
          Arrays.asList(
              "TrackingResult.java",
              "TrackingDiagnostics.java",
              "TrackingMode.java",
              "TrackingModeConfig.java",
              "PersonBlob.java",
              "PersonTargetSelector.java",
              "DepthBlobClusterer.java",
              "DepthBlobLimbScanner.java",
              "KinectDebugOverlayView.java",
              "KinectDebugRenderer.java",
              "PoseOverlayView.java",
              "KinectWebBridge.java",
              "AstraDepthController.java",
              "CameraController.java",
              "GoogleVisionController.java",
              "TFLitePoseProcessor.java",
              "OpenCVDepthFilter.java",
              "PoseLandmarkData.java",
              "PoseFrame.java",
              "OpenNIHelper.java",
              "ArmElevationTracker.java",
              "ArmElevationMapper.java"));

  private static final Pattern SIGNAL_CLASS_NAME =
      Pattern.compile("(Classifier|Detector|Segmenter|Tracker|Fusion|Anatomy|Processor)");

  private static final Pattern TEMPORAL_MARKER =
      Pattern.compile(
          "Prev|prev|hysteresis|Hysteresis|EMA|ema|median|Median|cooldown|COOLDOWN|smooth|Schmitt"
              + "|baseline|Latch|latch|WINDOW|window|history|reversal|lastGesture|smoothWith"
              + "|updateProximityLatch|lastDetectionTime|LOST_TIMEOUT|TIMEOUT_MS|ZERO_CROSS"
              + "|MAX_AGE_MS|updateLeftArmState|updateRightArmState|lastSwingTime|lastHandY"
              + "|gestureClassifier");

  private static final Pattern NAIVE_DEPTH_IF =
      Pattern.compile(
          "if\\s*\\([^)]*\\b(depth|distanceZ|distancia|distanceMm)\\w*\\s*[<>]=?\\s*\\d{3,}\\b");

  private static final Pattern NAIVE_DELTA_IF =
      Pattern.compile("if\\s*\\([^)]*deltaFromBaseline\\s*[<>]");

  private static final Pattern NAIVE_GESTURE_TRUE =
      Pattern.compile("\\.(isJumping|isDucking)\\s*=\\s*true");

  private static final Pattern NAIVE_PLAYER_DEPTH =
      Pattern.compile("isPlayerPresent\\s*=.*\\b(depth|distanceZ|distancia)\\s*[<>]");

  @Test
  public void trackingAndLogicMustUseTemporalSignalsNotNaiveThresholds() throws IOException {
    List<String> violations = new ArrayList<>();

    for (String pkg : Arrays.asList("tracking", "logic")) {
      Path pkgRoot = resolveMainJava(pkg);
      if (!Files.isDirectory(pkgRoot)) {
        continue;
      }
      try (java.util.stream.Stream<Path> paths = Files.walk(pkgRoot)) {
        paths
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(p -> checkProductionFile(p, violations));
      }
    }

    if (!violations.isEmpty()) {
      throw new AssertionError(
          "FSM-GUARD: heurísticas sem estado detectadas:\n" + String.join("\n", violations));
    }
  }

  private static void checkProductionFile(Path file, List<String> violations) {
    String name = file.getFileName().toString();
    if (EXEMPT_FILES.contains(name) || name.endsWith("View.java") || name.endsWith("Bridge.java")) {
      return;
    }

    try {
      String content = new String(Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8);
      String rel = file.toString().replace('\\', '/');
      boolean hasTemporal = TEMPORAL_MARKER.matcher(content).find();

      if (SIGNAL_CLASS_NAME.matcher(name).find() && !hasTemporal) {
        violations.add(
            rel
                + ": classificador/rastreador sem marcador temporal (Prev, hysteresis, EMA, latch,"
                + " cooldown, history)");
      }

      for (String line : content.split("\n")) {
        String trimmed = stripComment(line);
        if (trimmed.isEmpty()) {
          continue;
        }
        checkNaiveLine(rel, trimmed, hasTemporal, violations);
      }
    } catch (IOException e) {
      violations.add(file + ": erro ao ler arquivo");
    }
  }

  private static void checkNaiveLine(
      String rel, String line, boolean fileHasTemporal, List<String> violations) {
    if (NAIVE_DELTA_IF.matcher(line).find()) {
      violations.add(rel + ": if com deltaFromBaseline — use Schmitt-Trigger (Prev + ENTER/EXIT)");
    }

    if (NAIVE_DEPTH_IF.matcher(line).find() && !line.contains("MIN_") && !line.contains("MAX_")) {
      violations.add(
          rel + ": if com depth/distância literal mm — use latch, mediana ou constantes MIN_/MAX_");
    }

    if (NAIVE_GESTURE_TRUE.matcher(line).find() && !line.contains("Prev")) {
      violations.add(rel + ": isJumping/isDucking=true direto — derive de estado Prev");
    }

    if (NAIVE_PLAYER_DEPTH.matcher(line).find()) {
      violations.add(rel + ": isPlayerPresent por threshold depth — use blob/anatomia ou timeout");
    }

    if (!fileHasTemporal
        && line.contains("activeGesture =")
        && !line.contains("IDLE")
        && SIGNAL_CLASS_NAME.matcher(rel).find()) {
      violations.add(rel + ": atribui activeGesture sem pipeline temporal no arquivo");
    }
  }

  private static String stripComment(String line) {
    int idx = line.indexOf("//");
    return idx >= 0 ? line.substring(0, idx).trim() : line.trim();
  }

  private static Path resolveMainJava(String pkg) {
    Path direct = Paths.get("src/main/java/com/felipe/elftemplate", pkg);
    if (Files.isDirectory(direct)) {
      return direct;
    }
    return Paths.get("app/src/main/java/com/felipe/elftemplate", pkg);
  }
}
