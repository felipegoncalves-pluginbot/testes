package com.felipe.elftemplate.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public class TrackingResultCodecTest {

  @Test
  public void roundTripPreservesElevationAndGesture() throws Exception {
    TrackingResult original = new TrackingResult();
    original.reset();
    original.isPlayerPresent = true;
    original.leftHandElevation = 0.62f;
    original.rightHandElevation = 0.11f;
    original.activeGesture = KinectTrackingEngine.GestureType.LEFT_HAND_UP;
    original.head.set(0.48f, 0.21f, 1400);
    original.leftHand.set(0.22f, 0.54f, 1380);

    JSONObject json = TrackingResultCodec.toJson(original);
    TrackingResult decoded = new TrackingResult();
    TrackingResultCodec.applyJson(json, decoded);

    assertTrue(decoded.isPlayerPresent);
    assertEquals(0.62f, decoded.leftHandElevation, 0.001f);
    assertEquals(0.11f, decoded.rightHandElevation, 0.001f);
    assertEquals(KinectTrackingEngine.GestureType.LEFT_HAND_UP, decoded.activeGesture);
    assertEquals(0.48f, decoded.head.x, 0.001f);
    assertEquals(0.22f, decoded.leftHand.x, 0.001f);
  }
}
