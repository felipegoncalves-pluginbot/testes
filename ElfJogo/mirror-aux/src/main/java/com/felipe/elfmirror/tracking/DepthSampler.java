package com.felipe.elfmirror.tracking;

/** Amostra depth Astra (mm) em coordenadas normalizadas. */
public final class DepthSampler {

  private static final int MIN_VALID_DEPTH_MM = 500;
  private static final int MAX_VALID_DEPTH_MM = 2200;

  private DepthSampler() {}

  public static int sampleAtNorm(short[] depthMm, int width, int height, float normX, float normY) {
    if (depthMm == null || width <= 0 || height <= 0) {
      return 0;
    }
    float x = clamp01(normX);
    float y = clamp01(normY);
    int px = (int) (x * (width - 1));
    int py = (int) (y * (height - 1));
    int best = 0;
    int count = 0;
    for (int dy = -1; dy <= 1; dy++) {
      int sy = py + dy;
      if (sy < 0 || sy >= height) {
        continue;
      }
      int row = sy * width;
      for (int dx = -1; dx <= 1; dx++) {
        int sx = px + dx;
        if (sx < 0 || sx >= width) {
          continue;
        }
        int d = depthMm[row + sx] & 0xFFFF;
        if (d >= MIN_VALID_DEPTH_MM && d <= MAX_VALID_DEPTH_MM) {
          best += d;
          count++;
        }
      }
    }
    return count > 0 ? best / count : 0;
  }

  public static void writeJoint(
      com.felipe.elfmirror.protocol.Joint dest,
      float normX,
      float normY,
      short[] depthMm,
      int depthW,
      int depthH) {
    if (dest == null) {
      return;
    }
    float x = clamp01(normX);
    float y = clamp01(normY);
    dest.set(x, y, sampleAtNorm(depthMm, depthW, depthH, x, y));
  }

  private static float clamp01(float value) {
    if (value < 0f) {
      return 0f;
    }
    if (value > 1f) {
      return 1f;
    }
    return value;
  }
}
