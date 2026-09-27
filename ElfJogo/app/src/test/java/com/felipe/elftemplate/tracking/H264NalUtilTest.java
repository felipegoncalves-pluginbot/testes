package com.felipe.elftemplate.tracking;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class H264NalUtilTest {

  @Test
  public void detectsIdrWithFourByteStartCode() {
    byte[] au = new byte[] {0, 0, 0, 1, 0x65, 0x11, 0x22};
    assertTrue(H264NalUtil.containsIdr(au, au.length));
    assertFalse(H264NalUtil.containsSpsOrPps(au, au.length));
  }

  @Test
  public void detectsSpsWithThreeByteStartCode() {
    byte[] au = new byte[] {0, 0, 1, 0x67, 0x42};
    assertTrue(H264NalUtil.containsSpsOrPps(au, au.length));
    assertFalse(H264NalUtil.containsIdr(au, au.length));
  }

  @Test
  public void pFrameIsNotRecovery() {
    byte[][] frames =
        new byte[][] {
          new byte[] {0, 0, 0, 1, 0x41, 0x00},
          new byte[] {0, 0, 1, 0x01, 0x00}
        };
    for (int i = 0; i < frames.length; i++) {
      assertFalse(com.felipe.elftemplate.tracking.H264NalUtil.containsIdr(frames[i], frames[i].length));
      assertFalse(H264NalUtil.containsSpsOrPps(frames[i], frames[i].length));
    }
  }
}
