package com.felipe.elftemplate.architecture;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.Test;

/**
 * Palco do espelho: um único flip no canvas da câmera. {@code View.scaleX=-1} no pai invertia a
 * Astra (já OpenNI-mirrored) e congelava o preview no API 23.
 */
public class MirrorDisplayGuardTest {

  @Test
  public void mirrorLayoutMustNotUseViewScaleX() throws IOException {
    String xml = read("app/src/main/res/layout/activity_mirror.xml");
    Assert.assertFalse(
        "activity_mirror.xml não pode usar android:scaleX (layer de hardware + Astra invertida)",
        xml.contains("android:scaleX"));
  }

  @Test
  public void previewMirrorsOnCanvasNotViewProperty() throws IOException {
    String src = read("app/src/main/java/com/felipe/elftemplate/tracking/Nv21PreviewView.java");
    Assert.assertTrue(src.contains("canvas.scale(-1f, 1f)"));
    Assert.assertTrue(src.contains("setMirrored"));
    Assert.assertFalse(src.contains("YuvJpegBitmapRenderer"));
  }

  @Test
  public void mirrorActivityEnablesCanvasMirrorOnRgbOnly() throws IOException {
    String src = read("app/src/main/java/com/felipe/elftemplate/MirrorActivity.java");
    Assert.assertTrue(src.contains("setMirrored(true)"));
    Assert.assertFalse(src.contains("setScaleX"));
  }

  @Test
  public void fusionMustNotInventHandsWhenWristIsLost() throws IOException {
    String src = read("app/src/main/java/com/felipe/elftemplate/tracking/PoseDepthFusionArms.java");
    Assert.assertFalse(
        "fuseArm não deve chamar extrapolateHandFromElbow (Kinect NotTracked)",
        src.contains("extrapolateHandFromElbow(hand"));
    String engine = read("app/src/main/java/com/felipe/elftemplate/tracking/KinectTrackingEngine.java");
    Assert.assertTrue(
        "proximidade deve desligar fusão RGB",
        engine.contains("PoseDepthFusion.allowRgbFusion"));
  }

  @Test
  public void overlayDoesNotInvertNormalizedX() throws IOException {
    String src = read("app/src/main/java/com/felipe/elftemplate/tracking/KinectDebugOverlayView.java");
    Assert.assertFalse(src.contains("1.0f - "));
    Assert.assertFalse(src.contains("1f - "));
    Assert.assertTrue(src.contains("PreviewViewport.mapX"));
    Assert.assertTrue(
        "fullscreen 4:3 deve letterbox, não esticar no 16:9", src.contains("letterboxDest"));
  }

  /** HD e Astra estão na cabeça e giram juntas: pan pelo yaw desalinha esqueleto e imagem. */
  @Test
  public void overlayDoesNotPanWithHeadYaw() throws IOException {
    String[] files = {
      "app/src/main/java/com/felipe/elftemplate/mirror/MirrorSessionController.java",
      "app/src/main/java/com/felipe/elftemplate/tracking/KinectDebugOverlayView.java",
      "app/src/main/java/com/felipe/elftemplate/tracking/PreviewViewport.java",
      "app/src/main/java/com/felipe/elftemplate/MirrorActivity.java"
    };
    for (String file : files) {
      String src = read(file);
      Assert.assertFalse(file + " não deve compensar yaw no overlay", src.contains("PanNorm"));
      Assert.assertFalse(file + " não deve compensar yaw no overlay", src.contains("panNorm"));
    }
  }

  private static String read(String relative) throws IOException {
    Path path = Paths.get("app").resolve(relative);
    if (!Files.exists(path)) {
      path = Paths.get(relative);
    }
    if (!Files.exists(path) && relative.startsWith("app/")) {
      path = Paths.get(relative.substring("app/".length()));
    }
    return new String(Files.readAllBytes(path), Charset.forName("UTF-8"));
  }
}
