package com.felipe.elftemplate.tracking;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Garante que processDepthFrame reutiliza TrackingResult e Joints (zero-alloc). */
public class JointReuseHotPathTest {

  @Test
  public void processDepthFrameReusesResultAndJoints() {
    KinectTrackingEngine engine = new KinectTrackingEngine();
    int w = SyntheticDepthFixtures.DEFAULT_WIDTH;
    int h = SyntheticDepthFixtures.DEFAULT_HEIGHT;
    short[] frame =
        SyntheticDepthFixtures.createStandingPerson(w, h, SyntheticDepthFixtures.PLAYER_BASE_DEPTH);

    TrackingResult first = engine.processDepthFrame(frame, w, h);
    assertTrue(first.isPlayerPresent);
    Joint head = first.head;
    Joint leftHand = first.leftHand;

    TrackingResult second = engine.processDepthFrame(frame, w, h);
    assertSame("TrackingResult deve ser a mesma instância", first, second);
    assertSame("head Joint deve ser reutilizado", head, second.head);
    assertSame("leftHand Joint deve ser reutilizado", leftHand, second.leftHand);
  }
}
