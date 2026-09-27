package com.felipe.elftemplate.tracking;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ArmElevationBridgeTest {

  @Test
  public void chinRestInProximityDoesNotRaiseWing() {
    TrackingResult result = new TrackingResult();
    result.isPlayerPresent = true;
    result.diagnostics.isNearProximityMode = true;
    result.head.set(0.40f, 0.35f, 740);
    result.leftShoulder.set(0.32f, 0.45f, 740);
    result.rightShoulder.set(0.52f, 0.45f, 740);
    result.leftHand.set(0.30f, 0.62f, 740);
    result.rightHand.set(0.42f, 0.36f, 740);
    KinectGestureClassifier classifier = new KinectGestureClassifier();
    for (int i = 0; i < 6; i++) {
      com.felipe.elftemplate.tracking.ArmElevationBridge.syncFromJoints(result, classifier);
    }
    assertFalse("mão no queixo não é braço erguido", result.isRightHandRaised);
    assertTrue(result.rightHandElevation < 0.35f);
    assertFalse(result.isLeftHandRaised);
  }

  @Test
  public void handsUpAboveHeadStillRaises() {
    TrackingResult result = new TrackingResult();
    result.isPlayerPresent = true;
    result.diagnostics.isNearProximityMode = true;
    result.head.set(0.50f, 0.35f, 740);
    result.leftShoulder.set(0.40f, 0.45f, 740);
    result.rightShoulder.set(0.60f, 0.45f, 740);
    result.leftHand.set(0.38f, 0.18f, 740);
    result.rightHand.set(0.62f, 0.18f, 740);
    KinectGestureClassifier classifier = new KinectGestureClassifier();
    for (int i = 0; i < 6; i++) {
      ArmElevationBridge.syncFromJoints(result, classifier);
    }
    assertTrue(result.isLeftHandRaised);
    assertTrue(result.isRightHandRaised);
  }
}
