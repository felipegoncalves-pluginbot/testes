package com.felipe.elftemplate.logic;

import static org.junit.Assert.*;

import com.felipe.elftemplate.tracking.KinectTrackingEngine;
import org.junit.Before;
import org.junit.Test;

public class PoseMatchGameEngineTest {

  private PoseMatchGameEngine engine;

  @Before
  public void setUp() {
    engine = new PoseMatchGameEngine();
  }

  @Test
  public void testTargetPoseGenerationAndMatch() {
    engine.setTargetPose(KinectTrackingEngine.GestureType.T_POSE, 5.0f);
    assertEquals(KinectTrackingEngine.GestureType.T_POSE, engine.getCurrentTargetPose());

    int initialScore = engine.getScore();
    boolean matched = engine.evaluatePlayerPose(KinectTrackingEngine.GestureType.T_POSE, 0.5f);

    assertTrue("Pose should match target", matched);
    assertTrue("Score should increase on match", engine.getScore() > initialScore);
  }

  @Test
  public void testPoseMismatchDoesNotScore() {
    engine.setTargetPose(KinectTrackingEngine.GestureType.HANDS_UP, 5.0f);
    int initialScore = engine.getScore();

    boolean matched = engine.evaluatePlayerPose(KinectTrackingEngine.GestureType.DUCK, 0.5f);

    assertFalse("Pose should not match", matched);
    assertEquals("Score should remain unchanged", initialScore, engine.getScore());
  }

  @Test
  public void testPoseTimeout() {
    engine.setTargetPose(KinectTrackingEngine.GestureType.T_POSE, 2.0f);
    engine.update(2.5f); // Advance time past limit

    assertTrue("Round should be timed out", engine.isRoundTimedOut());
  }
}
