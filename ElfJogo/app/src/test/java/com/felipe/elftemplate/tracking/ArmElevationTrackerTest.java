package com.felipe.elftemplate.tracking;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

/** Testes para elevação de braço com EMA + Schmitt (anti-oscilação). */
public class ArmElevationTrackerTest {

  private ArmElevationTracker tracker;

  @Before
  public void setUp() {
    tracker = new ArmElevationTracker();
  }

  @Test
  public void handRaisedThenPartialDropKeepsRaisedUntilExitBand() {
    float shoulder = 0.50f;
    for (int i = 0; i < 5; i++) {
      tracker.update(shoulder, 0.35f, true, true);
    }
    assertTrue(tracker.isRaised());

    tracker.update(shoulder, 0.52f, true, true);
    assertTrue("Histerese mantém levantada dentro da banda de saída", tracker.isRaised());

    for (int i = 0; i < 8; i++) {
      tracker.update(shoulder, 0.88f, true, true);
    }
    assertFalse("Mão abaixada deve descer após EMA cruza banda de saída", tracker.isRaised());
  }

  @Test
  public void forwardReachWithoutSeparationCapsElevation() {
    float shoulder = 0.50f;
    for (int i = 0; i < 6; i++) {
      tracker.update(shoulder, 0.20f, true, false);
    }
    assertTrue(tracker.getSmoothedElevation() <= ArmElevationTracker.FORWARD_REACH_CAP + 0.05f);
  }

  @Test
  public void missingHandDecaysElevation() {
    float shoulder = 0.50f;
    for (int i = 0; i < 4; i++) {
      tracker.update(shoulder, 0.30f, true, true);
    }
    assertTrue(tracker.getSmoothedElevation() > 0.2f);

    for (int i = 0; i < 20; i++) {
      tracker.update(shoulder, 0.80f, false, false);
    }
    assertTrue(tracker.getSmoothedElevation() < 0.10f);
    assertFalse(tracker.isRaised());
  }
}
