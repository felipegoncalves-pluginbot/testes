package com.felipe.elftemplate.movement;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Tennis FATAL: {@code RejectedExecutionException} após {@code stopAll()} enquanto o callback de
 * tracking ainda chama {@code trackTargetX} na UI.
 */
public class RobotGameFeedbackLifecycleTest {

  @Test
  public void stopAllThenTrackDoesNotThrow() {
    RobotGameFeedback feedback = new RobotGameFeedback(null, null, null, null);
    assertFalse(feedback.isStopped());
    for (int i = 0; i < 4; i++) {
      feedback.trackTargetX(0.2f + i * 0.1f);
      feedback.setMirrorWingAngles(10 * i, 5 * i);
    }
    feedback.stopAll();
    assertTrue(feedback.isStopped());
    feedback.trackTargetX(0.8f);
    feedback.setMirrorHead2D(12, 4);
    feedback.setMirrorWingAngles(40, 50);
    feedback.speak("depois do stop");
    feedback.stopAll();
    assertTrue("stopAll é idempotente", feedback.isStopped());
  }
}
