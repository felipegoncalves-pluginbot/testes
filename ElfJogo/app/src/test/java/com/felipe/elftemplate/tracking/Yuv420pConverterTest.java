package com.felipe.elftemplate.tracking;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class Yuv420pConverterTest {

  @Test
  public void toNv21_interleavesUAndVPlanes() {
    int w = 4;
    int h = 4;
    int frame = w * h;
    int q = frame / 4;
    byte[] i420 = new byte[frame + 2 * q];
  for (int i = 0; i < frame; i++) {
      i420[i] = (byte) i;
    }
    for (int i = 0; i < q; i++) {
      i420[frame + i] = (byte) (100 + i);
      i420[frame + q + i] = (byte) (200 + i);
    }
    byte[] nv21 = new byte[frame + 2 * q];
    Yuv420pConverter.toNv21(i420, nv21, w, h);
    assertEquals((byte) 200, nv21[frame]);
    assertEquals((byte) 100, nv21[frame + 1]);
    assertEquals((byte) 201, nv21[frame + 2]);
    assertEquals((byte) 101, nv21[frame + 3]);
  }
}
