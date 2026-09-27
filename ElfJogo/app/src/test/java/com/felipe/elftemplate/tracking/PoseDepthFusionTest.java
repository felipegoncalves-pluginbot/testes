package com.felipe.elftemplate.tracking;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PoseDepthFusionTest {

  @Test
  public void testSampleDepthMmReturnsMedianInWindow() {
    int width = 64;
    int height = 48;
    short[] depthMap = new short[width * height];
    for (int i = 0; i < depthMap.length; i++) {
      depthMap[i] = 3500;
    }

    for (int y = 20; y <= 28; y++) {
      for (int x = 28; x <= 36; x++) {
        depthMap[y * width + x] = 700;
      }
    }

    int z = PoseDepthFusion.sampleDepthMm(32, 24, depthMap, width, height, 64, 48);
    assertEquals(700, z);
  }

  @Test
  public void testSampleDepthMmIgnoresInvalidDepth() {
    int width = 64;
    int height = 48;
    short[] depthMap = new short[width * height];
    for (int i = 0; i < depthMap.length; i++) {
      depthMap[i] = 100;
    }

    int z = PoseDepthFusion.sampleDepthMm(32, 24, depthMap, width, height, 64, 48);
    assertEquals(0, z);
  }
}
