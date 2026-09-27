package com.felipe.elftemplate.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

import org.junit.Test;

public class RgbPreviewBridgeTest {

  @Test
  public void offerDeliversCopyOnSameThread() {
    RgbPreviewBridge bridge = new RgbPreviewBridge();
    final int[] seen = new int[2];
    final byte[][] held = new byte[1][];
    bridge.setListener(
        new RgbPreviewBridge.Listener() {
          @Override
          public void onRgbPreviewFrame(byte[] nv21, int width, int height) {
            seen[0] = width;
            seen[1] = height;
            held[0] = nv21;
          }
        });
    byte[] src = new byte[6];
    src[0] = 7;
    com.felipe.elftemplate.tracking.RgbPreviewBridge live = bridge;
    for (int i = 0; i < 2; i++) {
      src[0] = (byte) (7 + i);
      live.offer(src, 2, 2, 6);
    }
    assertEquals(2, seen[0]);
    assertEquals(2, seen[1]);
    assertEquals(8, held[0][0]);
    assertNotSame(src, held[0]);
  }
}
