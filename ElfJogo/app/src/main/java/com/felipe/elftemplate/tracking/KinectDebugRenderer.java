package com.felipe.elftemplate.tracking;

import android.graphics.Bitmap;
import android.graphics.Color;

/** Renderizador de debug para visualização da silhueta de profundidade em Picture-in-Picture. */
public class KinectDebugRenderer {

  public static final int DEBUG_BMP_W = 80;
  public static final int DEBUG_BMP_H = 60;
  /** Alpha ~35% — silhueta visível sem ofuscar o preview RGB (Kinect body-mask style). */
  private static final int OVERLAY_BODY_ARGB = Color.argb(90, 0, 255, 102);

  public static void renderToBitmap(
      short[] depthData, int width, int height, Bitmap bitmap, int[] pixels) {
    renderToBitmap(depthData, width, height, bitmap, pixels, false);
  }

  public static void renderToBitmap(
      short[] depthData,
      int width,
      int height,
      Bitmap bitmap,
      int[] pixels,
      boolean transparentBackground) {
    if (bitmap == null || pixels == null || depthData == null) return;
    float stepX = (float) width / DEBUG_BMP_W;
    float stepY = (float) height / DEBUG_BMP_H;

    for (int y = 0; y < DEBUG_BMP_H; y++) {
      int rowOffset = ((int) (y * stepY)) * width;
      for (int x = 0; x < DEBUG_BMP_W; x++) {
        short depth = depthData[rowOffset + (int) (x * stepX)];
        if (depth >= DepthHistogramSegmenter.MIN_DEPTH_MM
            && depth <= DepthHistogramSegmenter.MAX_DEPTH_MM) {
          pixels[y * DEBUG_BMP_W + x] =
              transparentBackground ? OVERLAY_BODY_ARGB : Color.rgb(0, 200, 100);
        } else {
          pixels[y * DEBUG_BMP_W + x] =
              transparentBackground ? Color.TRANSPARENT : Color.rgb(8, 12, 20);
        }
      }
    }
    try {
      bitmap.setPixels(pixels, 0, DEBUG_BMP_W, 0, 0, DEBUG_BMP_W, DEBUG_BMP_H);
    } catch (Throwable ignored) {
    }
  }
}
