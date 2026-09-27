package com.felipe.elftemplate.tracking;

import java.util.Arrays;

/** Segmentador de profundidade baseado em histograma 3D e detecção de picos para o Sanbot Elf. */
public class DepthHistogramSegmenter {

  public static final int MIN_DEPTH_MM = 500;
  public static final int MAX_DEPTH_MM = 2200;
  public static final int NEAR_PROXIMITY_ENTER_MM = 880;
  public static final int NEAR_PROXIMITY_EXIT_MM = 1020;
  public static final int HIST_BIN_SIZE_MM = 25;
  public static final int HIST_BINS = (MAX_DEPTH_MM - MIN_DEPTH_MM) / HIST_BIN_SIZE_MM;

  private boolean proximityModeLatch = false;

  /** Calcula o histograma de profundidade descartando valores fora do alcance. */
  public int[] computeHistogram(short[] depthData, int width, int height) {
    int[] histogram = new int[HIST_BINS];
    if (depthData == null) {
      return histogram;
    }

    int step = (width >= 320) ? 2 : 1;
    for (int y = 0; y < height; y += step) {
      int rowOffset = y * width;
      for (int x = 0; x < width; x += step) {
        int depth = depthData[rowOffset + x] & 0xFFFF;
        if (depth >= MIN_DEPTH_MM && depth <= MAX_DEPTH_MM) {
          int bin = (depth - MIN_DEPTH_MM) / HIST_BIN_SIZE_MM;
          if (bin >= 0 && bin < HIST_BINS) {
            histogram[bin]++;
          }
        }
      }
    }
    return histogram;
  }

  /** Localiza o pico de profundidade com janela móvel de suavização. */
  public int findPeakDepth(int[] histogram) {
    int bestPeakBin = -1;
    int maxDensity = 0;

    for (int b = 1; b < HIST_BINS - 1; b++) {
      int density = histogram[b - 1] + (histogram[b] * 2) + histogram[b + 1];
      if (density > maxDensity && density > 120) {
        maxDensity = density;
        bestPeakBin = b;
      }
    }

    if (bestPeakBin == -1) {
      return 0;
    }
    return MIN_DEPTH_MM + (bestPeakBin * HIST_BIN_SIZE_MM) + (HIST_BIN_SIZE_MM / 2);
  }

  /** Atualiza o estado da histerese de proximidade. */
  public void updateProximityLatch(int referenceZ) {
    if (proximityModeLatch) {
      if (referenceZ > NEAR_PROXIMITY_EXIT_MM) {
        proximityModeLatch = false;
      }
    } else if (referenceZ < NEAR_PROXIMITY_ENTER_MM && referenceZ >= MIN_DEPTH_MM) {
      proximityModeLatch = true;
    }
  }

  public boolean isProximityMode() {
    return proximityModeLatch;
  }

  public void reset() {
    proximityModeLatch = false;
  }

  /** Calcula a profundidade mediana de um vetor de amostras. */
  public static int calculateMedian(int[] samples, int count) {
    if (samples == null || count <= 0) {
      return 0;
    }
    int actualCount = Math.min(count, samples.length);
    int[] copy = new int[actualCount];
    System.arraycopy(samples, 0, copy, 0, actualCount);
    Arrays.sort(copy);
    if ((actualCount & 1) == 1) {
      return copy[actualCount / 2];
    }
    return (copy[(actualCount / 2) - 1] + copy[actualCount / 2]) / 2;
  }
}
