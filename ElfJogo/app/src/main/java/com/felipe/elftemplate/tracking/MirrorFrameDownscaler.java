package com.felipe.elftemplate.tracking;

/** Downscale NV21 para offload WiFi sem alocar por frame no hot path. */
final class MirrorFrameDownscaler {

  private MirrorFrameDownscaler() {}

  static byte[] downscaleNv21(byte[] src, int srcW, int srcH, int dstW, int dstH, byte[] reuse) {
    if (src == null || srcW <= 0 || srcH <= 0 || dstW <= 0 || dstH <= 0) {
      return null;
    }
    int ySize = dstW * dstH;
    int uvSize = ySize / 2;
    int needed = ySize + uvSize;
    byte[] dst = reuse;
    if (dst == null || dst.length < needed) {
      dst = new byte[needed];
    }
    int xStep = Math.max(1, srcW / dstW);
    int yStep = Math.max(1, srcH / dstH);
    for (int y = 0; y < dstH; y++) {
      int srcY = y * yStep;
      if (srcY >= srcH) {
        srcY = srcH - 1;
      }
      int dstRow = y * dstW;
      int srcRow = srcY * srcW;
      for (int x = 0; x < dstW; x++) {
        int srcX = x * xStep;
        if (srcX >= srcW) {
          srcX = srcW - 1;
        }
        dst[dstRow + x] = src[srcRow + srcX];
      }
    }
    int srcUvRow = srcH;
    int dstUvRow = dstH;
    for (int y = 0; y < dstH / 2; y++) {
      int srcY = y * yStep;
      if (srcY >= srcH / 2) {
        srcY = (srcH / 2) - 1;
      }
      for (int x = 0; x < dstW / 2; x++) {
        int srcX = x * xStep;
        if (srcX >= srcW / 2) {
          srcX = (srcW / 2) - 1;
        }
        int dstIdx = ySize + y * dstW + x * 2;
        int srcIdx = srcW * srcH + srcY * srcW + srcX * 2;
        dst[dstIdx] = src[srcIdx];
        dst[dstIdx + 1] = src[srcIdx + 1];
      }
    }
    return dst;
  }

  static short[] downscaleDepth(short[] src, int srcW, int srcH, int dstW, int dstH, short[] reuse) {
    if (src == null || srcW <= 0 || srcH <= 0) {
      return null;
    }
    int needed = dstW * dstH;
    short[] dst = reuse;
    if (dst == null || dst.length < needed) {
      dst = new short[needed];
    }
    int xStep = Math.max(1, srcW / dstW);
    int yStep = Math.max(1, srcH / dstH);
    for (int y = 0; y < dstH; y++) {
      int srcY = Math.min(srcH - 1, y * yStep);
      int dstRow = y * dstW;
      int srcRow = srcY * srcW;
      for (int x = 0; x < dstW; x++) {
        int srcX = Math.min(srcW - 1, x * xStep);
        dst[dstRow + x] = src[srcRow + srcX];
      }
    }
    return dst;
  }
}
