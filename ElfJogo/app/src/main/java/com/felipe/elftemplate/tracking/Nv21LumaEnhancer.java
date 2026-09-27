package com.felipe.elftemplate.tracking;

/**
 * CLAHE leve no plano Y NV21 para palco / luz variável. Java puro, buffers reutilizados.
 */
final class Nv21LumaEnhancer {

  static final int TILES_X = 8;
  static final int TILES_Y = 6;
  private static final float CLIP_LIMIT = 2.0f;

  private final int[] hist = new int[256];
  private final int[] cdf = new int[256];
  private final byte[] tileLut = new byte[TILES_X * TILES_Y * 256];

  static final class LumaStats {
    int mean;
    int span;
  }

  LumaStats measure(byte[] yuv, int width, int height) {
    LumaStats stats = new LumaStats();
    int ySize = width * height;
    if (yuv == null || ySize <= 0 || yuv.length < ySize) {
      return stats;
    }
    measureYPlane(yuv, ySize, stats);
    return stats;
  }

  /** Só melhora cena escura, estourada ou sem contraste — CLAHE em luz boa distorce o MoveNet. */
  boolean needsEnhancement(LumaStats raw) {
    if (raw.span <= 0) {
      return false;
    }
    return raw.mean < 90
        || raw.mean > 170
        || raw.span < 80
        || raw.mean < PoseDepthFusion.MIN_USABLE_LUMA
        || raw.span < PoseDepthFusion.MIN_LUMA_SPAN;
  }

  LumaStats enhanceInPlace(byte[] yuv, int width, int height) {
    LumaStats stats = measure(yuv, width, height);
    int ySize = width * height;
    if (yuv == null || ySize <= 0 || yuv.length < ySize) {
      return stats;
    }
    buildTileLuts(yuv, width, height);
    applyTileLuts(yuv, width, height);
    measureYPlane(yuv, ySize, stats);
    if (stats.span < 16) {
      globalStretch(yuv, ySize, stats);
    }
    return stats;
  }

  private void buildTileLuts(byte[] yuv, int width, int height) {
    int tileW = Math.max(1, width / TILES_X);
    int tileH = Math.max(1, height / TILES_Y);
    int lutBase = 0;
    for (int ty = 0; ty < TILES_Y; ty++) {
      for (int tx = 0; tx < TILES_X; tx++) {
        buildOneTileLut(yuv, width, height, tx, ty, tileW, tileH, lutBase);
        lutBase += 256;
      }
    }
  }

  private void buildOneTileLut(
      byte[] yuv, int width, int height, int tx, int ty, int tileW, int tileH, int lutBase) {
    for (int i = 0; i < 256; i++) {
      hist[i] = 0;
    }
    int x0 = tx * tileW;
    int y0 = ty * tileH;
    int x1 = tx == TILES_X - 1 ? width : x0 + tileW;
    int y1 = ty == TILES_Y - 1 ? height : y0 + tileH;
    int count = accumulateTileHist(yuv, width, x0, y0, x1, y1);
    if (count == 0) {
      identityLut(lutBase);
      return;
    }
    writeClippedLut(lutBase, count);
  }

  private int accumulateTileHist(byte[] yuv, int width, int x0, int y0, int x1, int y1) {
    int count = 0;
    for (int y = y0; y < y1; y++) {
      int row = y * width;
      for (int x = x0; x < x1; x++) {
        hist[yuv[row + x] & 0xFF]++;
        count++;
      }
    }
    return count;
  }

  private void identityLut(int lutBase) {
    for (int i = 0; i < 256; i++) {
      tileLut[lutBase + i] = (byte) i;
    }
  }

  private void writeClippedLut(int lutBase, int count) {
    int clip = Math.max(1, (int) (count / 256f * CLIP_LIMIT));
    int excess = 0;
    for (int i = 0; i < 256; i++) {
      if (hist[i] > clip) {
        excess += hist[i] - clip;
        hist[i] = clip;
      }
    }
    int redist = excess / 256;
    for (int i = 0; i < 256; i++) {
      hist[i] += redist;
    }
    cdf[0] = hist[0];
    for (int i = 1; i < 256; i++) {
      cdf[i] = cdf[i - 1] + hist[i];
    }
    int cdfMin = 0;
    for (int i = 0; i < 256; i++) {
      if (cdf[i] != 0) {
        cdfMin = cdf[i];
        break;
      }
    }
    float scale = cdf[255] == cdfMin ? 0f : 255f / (cdf[255] - cdfMin);
    for (int i = 0; i < 256; i++) {
      int mapped = cdfMin == cdf[255] ? i : (int) ((cdf[i] - cdfMin) * scale + 0.5f);
      if (mapped < 0) {
        mapped = 0;
      } else if (mapped > 255) {
        mapped = 255;
      }
      tileLut[lutBase + i] = (byte) mapped;
    }
  }

  private void applyTileLuts(byte[] yuv, int width, int height) {
    int tileW = Math.max(1, width / TILES_X);
    int tileH = Math.max(1, height / TILES_Y);
    for (int y = 0; y < height; y++) {
      int ty = Math.min(TILES_Y - 1, y / tileH);
      int row = y * width;
      for (int x = 0; x < width; x++) {
        int tx = Math.min(TILES_X - 1, x / tileW);
        int lutBase = (ty * TILES_X + tx) * 256;
        int v = yuv[row + x] & 0xFF;
        yuv[row + x] = tileLut[lutBase + v];
      }
    }
  }

  private static void measureYPlane(byte[] yuv, int ySize, LumaStats stats) {
    long sum = 0;
    int min = 255;
    int max = 0;
    for (int i = 0; i < ySize; i++) {
      int v = yuv[i] & 0xFF;
      sum += v;
      if (v < min) {
        min = v;
      }
      if (v > max) {
        max = v;
      }
    }
    stats.mean = (int) (sum / ySize);
    stats.span = max - min;
  }

  private static void globalStretch(byte[] yuv, int ySize, LumaStats stats) {
    int min = 255;
    int max = 0;
    for (int i = 0; i < ySize; i++) {
      int v = yuv[i] & 0xFF;
      if (v < min) {
        min = v;
      }
      if (v > max) {
        max = v;
      }
    }
    if (max <= min) {
      return;
    }
    for (int i = 0; i < ySize; i++) {
      int v = yuv[i] & 0xFF;
      int stretched = (v - min) * 255 / (max - min);
      yuv[i] = (byte) stretched;
    }
    stats.mean = (min + max) / 2;
    stats.span = max - min;
  }
}
