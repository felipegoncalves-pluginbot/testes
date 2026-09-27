package com.felipe.elftemplate.architecture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.Test;

/** ROBOT-GUARD: Modo Espelho exige asas proporcionais e debounce de motores. */
public class MirrorMotorGuardTest {

  @Test
  public void mirrorSessionMustUseProportionalWingAngles() throws IOException {
    String source = readMainJava("mirror/MirrorSessionController.java");
    Assert.assertTrue(
        "MirrorSessionController deve chamar setMirrorWingAngles",
        source.contains("setMirrorWingAngles"));
    Assert.assertFalse(
        "MirrorSessionController não deve usar setMirrorWings binário",
        source.contains("setMirrorWings("));
  }

  @Test
  public void wingControllerMustSupportAbsoluteMirrorAngles() throws IOException {
    String source = readMainJava("movement/RobotWingController.java");
    Assert.assertTrue(source.contains("updateMirrorAngles"));
    Assert.assertTrue(source.contains("doAbsoluteAngleMotion"));
    Assert.assertTrue(source.contains("MIRROR_UPDATE_MIN_MS"));
    Assert.assertTrue(source.contains("MIRROR_ANGLE_DEADBAND"));
  }

  @Test
  public void feedbackMustSwallowRejectedExecutionAfterStop() throws IOException {
    String source = readMainJava("movement/RobotGameFeedback.java");
    Assert.assertTrue(source.contains("RejectedExecutionException"));
    Assert.assertTrue(source.contains("enqueue"));
  }

  @Test
  public void trackingStopMustDropCallback() throws IOException {
    String source = readMainJava("tracking/KinectTrackingEngine.java");
    Assert.assertTrue(
        "stop() deve anular callback antes do teardown (Tennis FATAL)",
        source.contains("callback = null"));
  }

  @Test
  public void mirrorEngineMustMapHandElevationToAngles() throws IOException {
    String logic = readMainJava("logic/MirrorGameEngine.java");
    Assert.assertTrue(logic.contains("getTargetLeftWingAngle"));
    Assert.assertTrue(logic.contains("ArmElevationMapper"));
    String tracking = readMainJava("tracking/ArmElevationTracker.java");
    Assert.assertTrue(tracking.contains("MIN_TOGGLE_MS"));
    Assert.assertTrue(tracking.contains("RAISE_ENTER_ELEV"));
  }

  private static String readMainJava(String relativePath) throws IOException {
    Path path =
        Paths.get("app/src/main/java/com/felipe/elftemplate").resolve(relativePath);
    if (!Files.exists(path)) {
      path = Paths.get("src/main/java/com/felipe/elftemplate").resolve(relativePath);
    }
    return new String(Files.readAllBytes(path), "UTF-8");
  }
}
