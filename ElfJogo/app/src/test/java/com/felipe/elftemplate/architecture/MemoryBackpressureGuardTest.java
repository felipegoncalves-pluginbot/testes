package com.felipe.elftemplate.architecture;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.Test;

/**
 * MEM-GUARD: pipelines de visão devem ter backpressure (1 frame em voo) e evitar clone() em hot path.
 */
public class MemoryBackpressureGuardTest {

  private static final Pattern CLONE_FRAME =
      Pattern.compile("(yuv|Yuv|yuvData|depthData|frameData)\\.clone\\s*\\(");

  private static final Pattern BACKPRESSURE_MARKER =
      Pattern.compile(
          "compareAndSet|setProcessing\\(true\\)|isProcessing\\(\\)|offer\\(|Frame descartado"
              + "|fila de entrada cheia");

  private static final Pattern BROKEN_GET_ONLY =
      Pattern.compile("isPoseProcessing\\.get\\(\\)\\s*\\)\\s*return");

  @Test
  public void visionPipelinesMustUseBackpressureNotPerFrameClone() throws IOException {
    List<String> violations = new ArrayList<>();

    scanDir(resolveDir("tracking"), violations);
    scanDir(resolveDir(""), violations);

    if (!violations.isEmpty()) {
      throw new AssertionError(
          "MEM-GUARD: risco de OOM em pipeline de visão:\n" + String.join("\n", violations));
    }
  }

  private static void scanDir(Path root, List<String> violations) throws IOException {
    if (!Files.isDirectory(root)) {
      return;
    }
    try (Stream<Path> paths = Files.walk(root)) {
      paths.filter(p -> p.toString().endsWith(".java")).forEach(p -> checkFile(p, violations));
    }
  }

  private static Path resolveDir(String sub) {
    Path direct = Paths.get("src/main/java/com/felipe/elftemplate", sub);
    if (Files.isDirectory(direct)) {
      return direct;
    }
    return Paths.get("app/src/main/java/com/felipe/elftemplate", sub);
  }

  private static void checkFile(Path file, List<String> violations) {
    String name = file.getFileName().toString();
    if (name.endsWith("Test.java")) {
      return;
    }

    try {
      String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
      String rel = file.toString().replace('\\', '/');

      if (BROKEN_GET_ONLY.matcher(content).find()) {
        violations.add(rel + ": isPoseProcessing.get() sem compareAndSet — enfileira clones infinitos");
      }

      if (CLONE_FRAME.matcher(content).find() && !BACKPRESSURE_MARKER.matcher(content).find()) {
        violations.add(
            rel + ": clone() de frame sem backpressure (compareAndSet / setProcessing / offer drop)");
      }

      if (name.contains("PoseProcessor") || name.contains("VisionMediaDecoder")) {
        if (!BACKPRESSURE_MARKER.matcher(content).find()) {
          violations.add(rel + ": processador de visão sem marcador de backpressure");
        }
      }

      if (content.contains("ArrayBlockingQueue<byte[]>")
          && content.contains("new ArrayBlockingQueue<byte[]>(10)")) {
        violations.add(rel + ": fila H.264 com capacidade 10 — reduzir a ≤2");
      }
    } catch (IOException e) {
      violations.add(file + ": erro ao ler");
    }
  }
}
