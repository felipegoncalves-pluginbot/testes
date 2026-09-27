package com.sanbot.debug;

/**
 * NV21 → ARGB com downsample, sem alocar. Usado só no hub de debug (≤2 Hz), não no
 * loop de tracking.
 */
public final class SanbotDebugRgb {

  private SanbotDebugRgb() {}

  public static void fillArgbDownsample(
      byte[] nv21, int srcW, int srcH, int[] argb, int dstW, int dstH) {
    if (nv21 == null || argb == null || srcW <= 0 || srcH <= 0 || dstW <= 0 || dstH <= 0) {
      return;
    }
    if (argb.length < dstW * dstH) {
      return;
    }
    int yPlane = srcW * srcH;
    int nvLen = nv21.length;
    for (int dy = 0; dy < dstH; dy++) {
      int srcY = (dy * srcH) / dstH;
      int yRow = srcY * srcW;
      int uvRow = yPlane + (srcY >> 1) * srcW;
      int dstRow = dy * dstW;
      for (int dx = 0; dx < dstW; dx++) {
        int srcX = (dx * srcW) / dstW;
        int yIdx = yRow + srcX;
        int uvIdx = uvRow + (srcX & ~1);
        if (yIdx >= nvLen || uvIdx + 1 >= nvLen) {
          argb[dstRow + dx] = 0xFF000000;
          continue;
        }
        int yVal = nv21[yIdx] & 0xFF;
        int v = (nv21[uvIdx] & 0xFF) - 128;
        int u = (nv21[uvIdx + 1] & 0xFF) - 128;
        int r = clamp(yVal + ((359 * v) >> 8));
        int g = clamp(yVal - ((88 * u + 183 * v) >> 8));
        int b = clamp(yVal + ((454 * u) >> 8));
        argb[dstRow + dx] = 0xFF000000 | (r << 16) | (g << 8) | b;
      }
    }
  }

  private static int clamp(int v) {
    if (v < 0) {
      return 0;
    }
    if (v > 255) {
      return 255;
    }
    return v;
  }
}
