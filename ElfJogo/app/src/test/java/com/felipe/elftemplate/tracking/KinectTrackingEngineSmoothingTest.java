package com.felipe.elftemplate.tracking;

import static org.junit.Assert.*;

import org.junit.Test;

/** Regressão: overlay usa resultado EMA — todos os joints devem seguir o frame bruto. */
public class KinectTrackingEngineSmoothingTest {

  @Test
  public void emaSmoothing_updatesElbowsAndShoulders_notOnlyHands() {
    KinectTrackingEngine engine = new KinectTrackingEngine();
    short[] frame =
        SyntheticDepthFixtures.createHandsUpPerson(
            SyntheticDepthFixtures.DEFAULT_WIDTH,
            SyntheticDepthFixtures.DEFAULT_HEIGHT,
            SyntheticDepthFixtures.PLAYER_BASE_DEPTH);
    TrackingResult raw = engine.processDepthFrame(frame, 64, 48);

    assertTrue(raw.isPlayerPresent);

    TrackingResult smoothed1 = engine.applyEmaSmoothingForTest(raw);
    TrackingResult smoothed2 = engine.applyEmaSmoothingForTest(raw);

    float defaultElbowX = 0.25f;
    assertTrue(
        "Cotovelo esq. deve convergir ao valor bruto, não ficar no default",
        Math.abs(smoothed2.leftElbow.x - raw.leftElbow.x) < 0.12f);
    assertTrue(
        "Cotovelo esq. não deve permanecer no default estático",
        Math.abs(smoothed2.leftElbow.x - defaultElbowX) > 0.03f
            || Math.abs(smoothed2.leftElbow.y - raw.leftElbow.y) < 0.12f);

    assertTrue(
        "Ombro esq. deve seguir frame bruto",
        Math.abs(smoothed2.leftShoulder.x - raw.leftShoulder.x) < 0.12f);

    assertEquals(smoothed2.leftHand.x, smoothed2.leftHandX, 0.001f);
    assertEquals(smoothed2.rightHand.x, smoothed2.rightHandX, 0.001f);
  }
}
