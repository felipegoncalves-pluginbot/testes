package com.felipe.elftemplate.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.felipe.elftemplate.tracking.Joint;
import com.felipe.elftemplate.tracking.TrackingResult;
import org.junit.Test;

public class HandCursorEngineTest {

  @Test
  public void mapsHandIntoLetterboxedScreen() {
    HandCursorEngine engine = new HandCursorEngine();
    TrackingResult result = standingWithLeftHand(0.5f, 0.5f, 1400, 1500);

    engine.update(result, 1280, 720);

    assertTrue(engine.isVisible());
    assertEquals(640f, engine.getScreenX(), 1f);
    assertEquals(360f, engine.getScreenY(), 1f);
  }

  @Test
  public void picksRaisedHandForCursor() {
    HandCursorEngine engine = new HandCursorEngine();
    TrackingResult result = new TrackingResult();
    result.isPlayerPresent = true;
    result.isLeftHandRaised = false;
    result.isRightHandRaised = true;
    result.leftHandElevation = 0.1f;
    result.rightHandElevation = 0.75f;
    result.leftShoulder.set(0.45f, 0.35f, 1500);
    result.rightShoulder.set(0.55f, 0.35f, 1500);
    result.leftHand.set(0.35f, 0.55f, 1500);
    result.rightHand.set(0.62f, 0.28f, 1500);
    result.diagnostics.rightHandPixelCount = 120;
    result.diagnostics.leftHandPixelCount = 20;

    engine.update(result, 1280, 720);

    assertTrue(engine.getScreenX() > 700f);
  }

  @Test
  public void hidesCursorWhenNoHandRaised() {
    HandCursorEngine engine = new HandCursorEngine();
    TrackingResult result = new TrackingResult();
    result.isPlayerPresent = true;
    result.isLeftHandRaised = false;
    result.isRightHandRaised = false;
    result.leftHand.set(0.35f, 0.55f, 1500);
    result.rightHand.set(0.62f, 0.28f, 1500);

    engine.update(result, 1280, 720);

    assertFalse(engine.isVisible());
  }

  @Test
  public void pushForwardTriggersSingleClickPulse() {
    HandCursorEngine engine = new HandCursorEngine();
    TrackingResult relaxed = standingWithLeftHand(0.5f, 0.5f, 1450, 1500);
    TrackingResult push = standingWithLeftHand(0.5f, 0.5f, 1360, 1500);

    engine.update(relaxed, 1280, 720);
    assertFalse(engine.consumeClickPulse());

    engine.update(push, 1280, 720);
    assertTrue(engine.consumeClickPulse());
    assertFalse(engine.consumeClickPulse());
  }

  @Test
  public void pushUsesSpineWhenShoulderZMissing() {
    Joint hand = new Joint(0.5f, 0.5f, 1380);
    Joint shoulder = new Joint(0.45f, 0.35f, 0);
    Joint spine = new Joint(0.5f, 0.4f, 1500);
    assertTrue(HandCursorEngine.isPushGesture(hand, shoulder, spine));
  }

  @Test
  public void dwellDoesNotRepeatWhileHandStaysStill() throws Exception {
    HandCursorEngine engine = new HandCursorEngine();
    TrackingResult stable = standingWithLeftHand(0.5f, 0.45f, 1450, 1500);
    int clicks = 0;
    for (int i = 0; i < 20; i++) {
      engine.update(stable, 1280, 720);
      if (engine.consumeClickPulse()) {
        clicks++;
      }
      Thread.sleep(90L);
    }
    assertEquals(1, clicks);
  }

  @Test
  public void dwellClickWhenHandStable() throws Exception {
    HandCursorEngine engine = new HandCursorEngine();
    TrackingResult stable = standingWithLeftHand(0.5f, 0.45f, 1450, 1500);
    for (int i = 0; i < 7; i++) {
      engine.update(stable, 1280, 720);
      Thread.sleep(90L);
    }
    assertTrue(engine.consumeClickPulse());
  }

  private static TrackingResult standingWithLeftHand(
      float hx, float hy, int handZ, int shoulderZ) {
    TrackingResult result = new TrackingResult();
    result.isPlayerPresent = true;
    result.isLeftHandRaised = true;
    result.leftHandElevation = 0.6f;
    result.leftShoulder.set(0.45f, 0.35f, shoulderZ);
    result.leftHand.set(hx, hy, handZ);
    result.spine.set(0.5f, 0.42f, shoulderZ);
    return result;
  }
}
