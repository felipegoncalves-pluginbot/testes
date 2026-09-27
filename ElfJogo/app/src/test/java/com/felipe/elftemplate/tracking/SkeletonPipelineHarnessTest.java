package com.felipe.elftemplate.tracking;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;

/**
 * Gera NDJSON local em .cursor/debug-938fa7.log quando ELF_SKELETON_HARNESS=1
 * (simula pipeline depth-blob + EMA sem dispositivo).
 */
public class SkeletonPipelineHarnessTest {

  private static final String SESSION = "938fa7";

  @Test
  public void harness_writesNdjsonWhenEnvSet() throws Exception {
    if (!"1".equals(System.getProperty("elf.skeleton.harness"))) {
      return;
    }

    KinectTrackingEngine engine = new KinectTrackingEngine();
    File root = resolveProjectRoot();
    File out = new File(root, "app/build/skeleton-harness-debug-938fa7.log");
    File parent = out.getParentFile();
    if (parent != null && !parent.exists()) {
      assertTrue(parent.mkdirs());
    }

    runScenario(engine, out, "hands_up_640", buildHandsUp640());
    runScenario(engine, out, "tpose_640", buildTPose640());
    runScenario(engine, out, "standing_640", buildStanding640());

    assertTrue(out.exists());
    assertTrue(out.length() > 0);
  }

  private static void runScenario(
      KinectTrackingEngine engine, File out, String label, short[] frame) throws Exception {
    int w = 640;
    int h = 480;
    TrackingResult raw = engine.processDepthFrame(frame, w, h);
    TrackingResult smooth1 = engine.applyEmaSmoothingForTest(raw);
    TrackingResult smooth2 = engine.applyEmaSmoothingForTest(raw);

    float hipFallback = raw.playerCentroidY + 0.18f;
    append(
        out,
        "harness",
        "SkeletonPipelineHarness",
        "scenario_" + label,
        "{\"leftPx\":"
            + raw.diagnostics.leftHandPixelCount
            + ",\"rawLHandY\":"
            + raw.leftHand.y
            + ",\"rawLElbowY\":"
            + raw.leftElbow.y
            + ",\"smoothLElbowY\":"
            + smooth2.leftElbow.y
            + ",\"smoothLHandY\":"
            + smooth2.leftHand.y
            + ",\"shoulderY\":"
            + raw.leftShoulder.y
            + ",\"hipFallbackY\":"
            + hipFallback
            + ",\"elbowNearDefault\":"
            + (Math.abs(smooth2.leftElbow.x - 0.25f) < 0.02f
                && Math.abs(smooth2.leftElbow.y - 0.45f) < 0.02f)
            + "}");

    assertFalse(
        "EMA não deve deixar cotovelo no default após 2 frames: " + label,
        Math.abs(smooth2.leftElbow.x - 0.25f) < 0.02f
            && Math.abs(smooth2.leftElbow.y - 0.45f) < 0.02f);

    if (label.contains("hands_up")) {
      assertTrue(raw.leftHand.y < raw.leftShoulder.y - 0.03f);
      assertTrue(Math.abs(raw.leftHand.y - hipFallback) > 0.05f);
    }
  }

  private static short[] buildHandsUp640() {
    short[] small =
        SyntheticDepthFixtures.createHandsUpPerson(64, 48, SyntheticDepthFixtures.PLAYER_BASE_DEPTH);
    short[] frame = SyntheticDepthFixtures.upscaleNearest(small, 64, 48, 640, 480);
    SyntheticDepthFixtures.injectSensorNoise(frame, 0.12f, 99L);
    SyntheticDepthFixtures.addSideBlob(frame, 640, 480, 480, 240, 35, (short) 1400);
    return frame;
  }

  private static short[] buildTPose640() {
    short[] small =
        SyntheticDepthFixtures.createTPosePerson(64, 48, SyntheticDepthFixtures.PLAYER_BASE_DEPTH);
    return SyntheticDepthFixtures.upscaleNearest(small, 64, 48, 640, 480);
  }

  private static short[] buildStanding640() {
    short[] small =
        SyntheticDepthFixtures.createStandingPerson(64, 48, SyntheticDepthFixtures.PLAYER_BASE_DEPTH);
    short[] frame = SyntheticDepthFixtures.upscaleNearest(small, 64, 48, 640, 480);
    SyntheticDepthFixtures.injectSensorNoise(frame, 0.15f, 77L);
    return frame;
  }

  private static File resolveProjectRoot() {
    File dir = new File(System.getProperty("user.dir"));
    if ("app".equals(dir.getName())) {
      return dir.getParentFile();
    }
    return dir;
  }

  private static void append(
      File out, String hypothesisId, String location, String message, String dataJson)
      throws Exception {
    long now = System.currentTimeMillis();
    String line =
        "{\"sessionId\":\""
            + SESSION
            + "\",\"hypothesisId\":\""
            + hypothesisId
            + "\",\"location\":\""
            + location
            + "\",\"message\":\""
            + message
            + "\",\"data\":"
            + dataJson
            + ",\"timestamp\":"
            + now
            + "}";
    FileOutputStream fos = new FileOutputStream(out, true);
    fos.write(line.getBytes("UTF-8"));
    fos.write('\n');
    fos.close();
  }
}
