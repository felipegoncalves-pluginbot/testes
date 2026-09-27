package com.felipe.elftemplate.tracking;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DepthBlobClustererTest {

  @Test
  public void clusterSurvivesUndersizedBufferWithoutCrash() {
    DepthBlobClusterer clusterer = new DepthBlobClusterer();
    short[] depth = new short[64 * 44];
    for (int i = 0; i < depth.length; i++) {
      depth[i] = 1400;
    }
    assertTrue(clusterer.cluster(depth, 64, 48).isEmpty());
  }

  @Test
  public void clusterFindsBlobOnSyntheticTorso() {
    DepthBlobClusterer clusterer = new DepthBlobClusterer();
    int w = 64;
    int h = 48;
    short[] depth = new short[w * h];
    for (int y = 8; y < 36; y++) {
      for (int x = 20; x < 44; x++) {
        depth[y * w + x] = 1500;
      }
    }
    assertFalse(clusterer.cluster(depth, w, h).isEmpty());
  }
}
