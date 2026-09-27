package com.felipe.elftemplate.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PoseFrameHoldTest {

  @Test
  public void applyTo_reusesSnapshotWithDecay() {
    PoseLandmarkData[] live = PoseLandmarkData.allocateKeypoints();
    for (int i = 0; i < PoseFrame.KEYPOINT_COUNT; i++) {
      live[i].set(0.1f * i, 0.2f, 0.8f);
    }
    live[PoseFrame.NOSE].set(0.42f, 0.18f, 0.9f);
    PoseFrameHold hold = new PoseFrameHold();
    hold.save(live);

    PoseLandmarkData[] out = PoseLandmarkData.allocateKeypoints();
    assertTrue(hold.applyTo(out));
    assertEquals(0.42f, out[PoseFrame.NOSE].x, 0.001f);
    assertEquals(0.18f, out[PoseFrame.NOSE].y, 0.001f);
    assertTrue(out[PoseFrame.NOSE].score > 0.85f);
    assertTrue(out[PoseFrame.NOSE].score <= 0.9f);
  }

  @Test
  public void applyTo_falseWhenEmpty() {
    PoseFrameHold hold = new PoseFrameHold();
    PoseLandmarkData[] out = PoseLandmarkData.allocateKeypoints();
    assertFalse(hold.applyTo(out));
  }
}
