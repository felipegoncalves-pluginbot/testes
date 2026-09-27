package com.felipe.elftemplate.tracking;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class Nv21LumaEnhancerTest {

  @Test
  public void enhanceInPlace_darkFrameIncreasesSpan() {
    int w = 64;
    int h = 48;
    byte[] yuv = new byte[w * h * 3 / 2];
    for (int i = 0; i < w * h; i++) {
      yuv[i] = (byte) 40;
    }
    yuv[0] = 20;
    yuv[4] = 60;
    Nv21LumaEnhancer enhancer = new Nv21LumaEnhancer();
    Nv21LumaEnhancer.LumaStats stats = enhancer.enhanceInPlace(yuv, w, h);
    assertTrue("média de cena escura < 90", stats.mean < 90);
    int min = 255;
    int max = 0;
    for (int i = 0; i < w * h; i++) {
      int v = yuv[i] & 0xFF;
      if (v < min) {
        min = v;
      }
      if (v > max) {
        max = v;
      }
    }
    assertTrue("CLAHE deve ampliar o span Y", max - min >= 120);
    assertTrue("span reportado bate com medição", stats.span == max - min);
  }

  @Test
  public void needsEnhancement_falseOnWellLitFrame() {
    int w = 64;
    int h = 48;
    byte[] yuv = new byte[w * h * 3 / 2];
    for (int i = 0; i < w * h; i++) {
      yuv[i] = (byte) 128;
    }
    Nv21LumaEnhancer enhancer = new Nv21LumaEnhancer();
    Nv21LumaEnhancer.LumaStats raw = enhancer.measure(yuv, w, h);
    assertFalse(enhancer.needsEnhancement(raw));
  }
}
