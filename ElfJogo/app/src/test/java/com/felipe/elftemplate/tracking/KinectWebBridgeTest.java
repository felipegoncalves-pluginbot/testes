package com.felipe.elftemplate.tracking;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

public class KinectWebBridgeTest {

  private KinectWebBridge bridge;
  private boolean scoreCalled = false;
  private boolean bombCalled = false;
  private boolean gameOverCalled = false;

  @Before
  public void setUp() {
    bridge =
        new KinectWebBridge(
            new KinectWebBridge.BridgeListener() {
              @Override
              public void onScoreEvent(int points, int combo) {
                scoreCalled = true;
              }

              @Override
              public void onBombEvent() {
                bombCalled = true;
              }

              @Override
              public void onGameOverEvent(int finalScore) {
                gameOverCalled = true;
              }

              @Override
              public void onTrackHeadEvent(float x) {}
            });
  }

  @Test
  public void testBridgeDispatchesScore() {
    bridge.onScore(100, 3);
    assertTrue("Score event should be triggered", scoreCalled);
  }

  @Test
  public void testBridgeDispatchesBomb() {
    bridge.onBombHit();
    assertTrue("Bomb event should be triggered", bombCalled);
  }

  @Test
  public void testBridgeDispatchesGameOver() {
    bridge.onGameOver(550);
    assertTrue("Game over event should be triggered", gameOverCalled);
  }

  @Test
  public void testFormatJsonData() {
    TrackingResult result = new TrackingResult();
    result.isPlayerPresent = true;
    result.playerCentroidX = 0.55f;
    result.playerCentroidY = 0.45f;
    result.playerDistanceZ = 1600;
    result.rightHandX = 0.75f;
    result.rightHandY = 0.30f;
    result.isRightHandRaised = true;
    result.activeGesture = KinectTrackingEngine.GestureType.SWING_UP;

    String json = bridge.formatTrackingJson(result);

    assertNotNull("JSON should not be null", json);
    assertTrue("Should contain isPlayerPresent", json.contains("\"isPlayerPresent\":true"));
    assertTrue("Should contain gesture", json.contains("\"gesture\":\"SWING_UP\""));
    assertTrue("Should contain distance", json.contains("\"distanceZ\":1600"));
    assertTrue("Should contain pose fusion flag", json.contains("\"isPoseFusionActive\""));
    assertTrue("Should contain head coordinates", json.contains("\"headX\""));
  }
}
