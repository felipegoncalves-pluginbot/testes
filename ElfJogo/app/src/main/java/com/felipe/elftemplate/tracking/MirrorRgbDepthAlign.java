package com.felipe.elftemplate.tracking;

/** Alinha NV21 da HD (16:9) ao FOV 4:3 da Astra antes do offload WiFi. */
final class MirrorRgbDepthAlign {

  private MirrorRgbDepthAlign() {}

  /** Recorta centro 4:3 (Kinect depth space). Para 1280×720 → 960×720 com offset X. */
  static boolean isFourThree(int width, int height) {
    if (width <= 0 || height <= 0) {
      return false;
    }
    return width * 3 == height * 4;
  }

  /**
   * @return {@code int[4]} offsetX, offsetY, cropW, cropH
   */
  static int[] cropFourThreeRect(int srcW, int srcH) {
  int[] rect = new int[4];
    if (srcW <= 0 || srcH <= 0) {
      return rect;
    }
    float srcAspect = (float) srcW / (float) srcH;
    float targetAspect = 4f / 3f;
    if (srcAspect > targetAspect) {
      rect[3] = srcH;
      rect[2] = (int) (srcH * targetAspect);
      rect[0] = (srcW - rect[2]) / 2;
      rect[1] = 0;
    } else {
      rect[2] = srcW;
      rect[3] = (int) (srcW / targetAspect);
      rect[0] = 0;
      rect[1] = (srcH - rect[3]) / 2;
    }
    return rect;
  }

  /** Recorta NV21; reutiliza {@code reuse} se grande o suficiente. */
  static byte[] cropNv21(byte[] src, int srcW, int srcH, int[] rect, byte[] reuse) {
    if (src == null || rect == null || rect.length < 4) {
      return null;
    }
    int offX = rect[0];
    int offY = rect[1];
    int cropW = rect[2];
    int cropH = rect[3];
    if (cropW <= 0 || cropH <= 0 || offX < 0 || offY < 0) {
      return null;
    }
    int ySize = cropW * cropH;
    int needed = ySize + ySize / 2;
    byte[] dst = reuse;
    if (dst == null || dst.length < needed) {
      dst = new byte[needed];
    }
    for (int y = 0; y < cropH; y++) {
      int srcRow = (offY + y) * srcW + offX;
      int dstRow = y * cropW;
      System.arraycopy(src, srcRow, dst, dstRow, cropW);
    }
    int srcUvRow = srcW * srcH;
    int dstUvRow = ySize;
    for (int y = 0; y < cropH / 2; y++) {
      int srcIdx = srcUvRow + (offY / 2 + y) * srcW + offX;
      int dstIdx = dstUvRow + y * cropW;
      System.arraycopy(src, srcIdx, dst, dstIdx, cropW);
    }
    return dst;
  }
}
