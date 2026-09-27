package com.felipe.elftemplate.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MirrorRgbDepthAlignTest {

  @Test
  public void poseEfficientStreamIsFourThree() {
    assertTrue(MirrorRgbDepthAlign.isFourThree(640, 480));
    assertFalse(MirrorRgbDepthAlign.isFourThree(1280, 720));
  }

  @Test
  public void cropRectForHd720pIsCenter960() {
    int[] rect = MirrorRgbDepthAlign.cropFourThreeRect(1280, 720);
    assertEquals(160, rect[0]);
    assertEquals(0, rect[1]);
    assertEquals(960, rect[2]);
    assertEquals(720, rect[3]);
  }

  @Test
  public void cropNv21PreservesCenterPixel() {
    int w = 8;
    int h = 6;
    byte[] src = new byte[w * h + w * h / 2];
    for (int i = 0; i < w * h; i++) {
      src[i] = (byte) i;
    }
    int[] rect = MirrorRgbDepthAlign.cropFourThreeRect(w, h);
    byte[] cropped = MirrorRgbDepthAlign.cropNv21(src, w, h, rect, null);
    assertNotNull(cropped);
    assertEquals(rect[2] * rect[3], cropped.length - cropped.length / 3);
    assertEquals(src[rect[0]], cropped[0]);
  }
}
