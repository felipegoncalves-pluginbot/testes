package com.felipe.elftemplate.architecture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.felipe.elftemplate.logic.MirrorGameEngine;
import com.felipe.elftemplate.movement.RobotHeadController;
import com.felipe.elftemplate.movement.RobotWingController;
import com.felipe.elftemplate.tracking.PoseDepthFusion;
import com.felipe.elftemplate.tracking.PoseFrame;
import com.felipe.elftemplate.tracking.PoseLandmarkData;
import com.felipe.elftemplate.tracking.SyntheticDepthFixtures;
import com.felipe.elftemplate.tracking.TFLitePoseProcessor;
import com.felipe.elftemplate.tracking.TrackingResult;
import org.junit.Test;

/**
 * Contrato executável de eixos. Se este teste e a spec divergirem, a spec está errada.
 * Ver docs/specs/COORDINATE_FRAMES.md.
 */
public class CoordinateContractTest {

  @Test
  public void cameraLeftProducesPositiveYawAndHardwareRightOfCenter() {
    MirrorGameEngine engine = new MirrorGameEngine();
    TrackingResult result = new TrackingResult();
    result.isPlayerPresent = true;
    result.head.set(0.25f, 0.30f, 1200);
    for (int i = 0; i < 8; i++) {
      engine.processTracking(result);
    }
    int yaw = engine.getTargetHeadYaw();
    int hw = RobotHeadController.calculateHardwareYaw(yaw);
    assertTrue("X=0.25 → yawOffset positivo", yaw > 0);
    assertTrue("X=0.25 → hardwareYaw > 90", hw > 90 && hw <= 150);
  }

  @Test
  public void cameraRightProducesNegativeYawAndHardwareLeftOfCenter() {
    MirrorGameEngine engine = new MirrorGameEngine();
    TrackingResult result = new TrackingResult();
    result.isPlayerPresent = true;
    result.head.set(0.75f, 0.30f, 1200);
    for (int i = 0; i < 8; i++) {
      engine.processTracking(result);
    }
    int yaw = engine.getTargetHeadYaw();
    int hw = RobotHeadController.calculateHardwareYaw(yaw);
    assertTrue("X=0.75 → yawOffset negativo", yaw < 0);
    assertTrue("X=0.75 → hardwareYaw < 90", hw < 90 && hw >= 30);
  }

  @Test
  public void moveNetOpticalXIsNotMirrored() {
    assertEquals(0.20f, TFLitePoseProcessor.opticalXFromMoveNet(0.20f), 0.0001f);
    assertEquals(0.80f, TFLitePoseProcessor.opticalXFromMoveNet(0.80f), 0.0001f);
  }

  @Test
  public void anatomicalRightShoulderMapsToScreenLeft() {
    PoseLandmarkData[] lms = new PoseLandmarkData[PoseFrame.KEYPOINT_COUNT];
    for (int i = 0; i < lms.length; i++) {
      lms[i] = new PoseLandmarkData(0.50f, 0.50f, 0.90f);
    }
    lms[PoseFrame.NOSE] = new PoseLandmarkData(0.50f, 0.20f, 0.95f);
    lms[PoseFrame.RIGHT_SHOULDER] = new PoseLandmarkData(0.35f, 0.35f, 0.90f);
    lms[PoseFrame.LEFT_SHOULDER] = new PoseLandmarkData(0.65f, 0.35f, 0.90f);
    lms[PoseFrame.RIGHT_WRIST] = new PoseLandmarkData(0.32f, 0.65f, 0.90f);
    lms[PoseFrame.LEFT_WRIST] = new PoseLandmarkData(0.68f, 0.65f, 0.90f);
    short[] depth =
        SyntheticDepthFixtures.createStandingPerson(
            64, 48, SyntheticDepthFixtures.PLAYER_BASE_DEPTH);
    TrackingResult result = new TrackingResult();
    PoseFrame frame = new PoseFrame(lms, System.currentTimeMillis(), 64, 48);
    assertTrue(PoseDepthFusion.tryFuse(result, frame, depth, 64, 48, frame.timestampMs));
    assertTrue("screen-left.x < screen-right.x", result.leftShoulder.x < result.rightShoulder.x);
  }

  @Test
  public void wingUpdatePeriodIsOneHundredFiftyMs() {
    assertEquals(150L, RobotWingController.MIRROR_UPDATE_MIN_MS);
  }
}
