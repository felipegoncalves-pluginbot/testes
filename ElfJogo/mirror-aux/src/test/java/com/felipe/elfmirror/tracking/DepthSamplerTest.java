package com.felipe.elfmirror.tracking;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DepthSamplerTest {

  @Test
  public void medianIgnoresInvalidDepth() {
    int width = 64;
    int height = 48;
    short[] depth = new short[width * height];
    int px = width / 2;
    int py = height / 2;
    depth[py * width + px] = (short) 1500;
    float normX = (float) px / (width - 1);
    float normY = (float) py / (height - 1);
    int z = DepthSampler.sampleAtNorm(depth, width, height, normX, normY);
    assertEquals(1500, z);
  }
}
