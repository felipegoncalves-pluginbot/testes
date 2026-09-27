package com.felipe.elftemplate.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PoseDepthFusionExtrapolationTest {

  @Test
  public void blendIgnoresDepthWhenItLooksLikeAFoot() {
    TrackingResult fused = new TrackingResult();
    fused.leftHand.set(0.30f, 0.40f, 1500);
    fused.rightHand.set(0.70f, 0.42f, 1500);
    PoseDepthHandBlend.blendVerticalWithDepth(fused, 0.89f, 0.90f);
    assertEquals(0.40f, fused.leftHand.y, 0.01f);
    assertEquals(0.42f, fused.rightHand.y, 0.01f);
  }

  @Test
  public void lostWristKeepsDepthBlobHandInsteadOfClampingToZero() {
    TrackingResult result = new TrackingResult();
    result.leftHand.set(0.32f, 0.58f, 700);
    result.rightHand.set(0.48f, 0.60f, 700);
    result.leftHandX = 0.32f;
    result.leftHandY = 0.58f;
    result.rightHandX = 0.48f;
    result.rightHandY = 0.60f;

    PoseLandmarkData[] lms = new PoseLandmarkData[PoseFrame.KEYPOINT_COUNT];
    for (int i = 0; i < lms.length; i++) {
      lms[i] = new PoseLandmarkData(0.45f, 0.45f, 0.85f);
    }
    lms[PoseFrame.NOSE] = new PoseLandmarkData(0.40f, 0.22f, 0.90f);
    lms[PoseFrame.RIGHT_SHOULDER] = new PoseLandmarkData(0.35f, 0.35f, 0.90f);
    lms[PoseFrame.LEFT_SHOULDER] = new PoseLandmarkData(0.55f, 0.35f, 0.90f);
    lms[PoseFrame.RIGHT_ELBOW] = new PoseLandmarkData(0.08f, 0.42f, 0.80f);
    lms[PoseFrame.LEFT_ELBOW] = new PoseLandmarkData(0.70f, 0.42f, 0.80f);
    lms[PoseFrame.RIGHT_WRIST] = new PoseLandmarkData(0.01f, 0.18f, 0.10f);
    lms[PoseFrame.LEFT_WRIST] = new PoseLandmarkData(0.90f, 0.18f, 0.10f);

    long now = System.currentTimeMillis();
    PoseFrame frame = new PoseFrame(lms, now, 64, 48);
    short[] depth =
        SyntheticDepthFixtures.createStandingPerson(
            64, 48, SyntheticDepthFixtures.PLAYER_BASE_DEPTH);
    boolean fused = com.felipe.elftemplate.tracking.PoseDepthFusion.tryFuse(result, frame, depth, 64, 48, now);
    assertTrue(fused);
    assertTrue("punho depth não pode ir para X=0 (extrapolação)", result.leftHand.x > 0.15f);
    assertEquals(0.32f, result.leftHand.x, 0.02f);
    assertEquals(0.48f, result.rightHand.x, 0.02f);
  }

  @Test
  public void extrapolateHand_extendsBeyondElbowAlongArmVector() {
    Joint shoulder = new Joint(0.5f, 0.4f, 1500);
    Joint elbow = new Joint(0.35f, 0.5f, 1500);
    short[] depth = new short[64 * 48];
    short[] bg = SyntheticDepthFixtures.createBackground(64, 48);
    for (int i = 0; i < bg.length; i++) {
      depth[i] = bg[i];
    }

    Joint hand = new Joint();
    PoseDepthFusionArms.extrapolateHandFromElbow(hand, shoulder, elbow, depth, 64, 48);

    assertTrue(hand.x < elbow.x);
    assertTrue(hand.y > elbow.y);
    float upperDx = elbow.x - shoulder.x;
    float upperDy = elbow.y - shoulder.y;
    float handDx = hand.x - elbow.x;
    float handDy = hand.y - elbow.y;
    assertEquals(upperDx, handDx, 0.02f);
    assertEquals(upperDy, handDy, 0.02f);
  }

  @Test
  public void reconcileElbowMidpoint_placesElbowBetweenShoulderAndHand() {
    TrackingResult result = new TrackingResult();
    result.leftShoulder = new Joint(0.5f, 0.4f, 1500);
    result.leftHand = new Joint(0.2f, 0.55f, 1500);

    PoseDepthFusionArms.reconcileElbowMidpointForTest(result, true);

    assertEquals(0.35f, result.leftElbow.x, 0.01f);
    assertEquals(0.475f, result.leftElbow.y, 0.01f);
  }
}
