package com.felipe.elftemplate.tracking;

import java.util.ArrayList;
import java.util.List;

/** Agrupa pixels de profundidade em blobs (player index simulado sem NUI Kinect). */
public class DepthBlobClusterer {

  private static final int NEAR_PROXIMITY_THRESHOLD_MM = 950;

  private int[] labels = new int[1];
  private int[] stack = new int[1];
  private final List<PersonBlob> blobs = new ArrayList<PersonBlob>(4);

  public List<PersonBlob> cluster(short[] depthData, int width, int height) {
    blobs.clear();
    if (depthData == null || width <= 0 || height <= 0) {
      return blobs;
    }
    int expectedPixels = width * height;
    if (depthData.length < expectedPixels) {
      return blobs;
    }

    int step = Math.max(1, width / 64);
    int maxGroundRow = (int) (height * 0.90f);
    int gridW = (width + step - 1) / step;
    int gridH = (maxGroundRow + step - 1) / step;
    int cells = gridW * gridH;
    if (labels.length < cells) {
      labels = new int[cells];
      stack = new int[cells];
    } else {
      for (int i = 0; i < cells; i++) {
        labels[i] = 0;
      }
    }
    int nextLabel = 1;

    for (int gy = 0; gy < gridH; gy++) {
      int y = gy * step;
      for (int gx = 0; gx < gridW; gx++) {
        int idx = gy * gridW + gx;
        if (labels[idx] != 0) {
          continue;
        }
        int x = gx * step;
        if (y >= height || x >= width) {
          continue;
        }
        short depth = depthData[y * width + x];
        if (!isHumanDepth(depth)) {
          continue;
        }
        floodFill(depthData, width, height, step, maxGroundRow, gridW, gridH, labels, gx, gy, nextLabel);
        nextLabel++;
      }
    }

    for (int label = 1; label < nextLabel; label++) {
      PersonBlob blob = buildBlob(label, depthData, width, height, step, maxGroundRow, gridW, gridH, labels);
      if (blob != null) {
        blobs.add(blob);
      }
    }
    return blobs;
  }

  private void floodFill(
      short[] depthData,
      int width,
      int height,
      int step,
      int maxGroundRow,
      int gridW,
      int gridH,
      int[] labels,
      int startGx,
      int startGy,
      int label) {
    int startY = startGy * step;
    int startX = startGx * step;
    if (startY >= height || startX >= width) {
      return;
    }
    int startIdx = startY * width + startX;
    if (startIdx < 0 || startIdx >= depthData.length) {
      return;
    }
    int refDepth = depthData[startIdx] & 0xFFFF;
    int band = refDepth < NEAR_PROXIMITY_THRESHOLD_MM ? 260 : 360;

    int sp = 0;
    stack[sp++] = startGy * gridW + startGx;

    while (sp > 0) {
      int cell = stack[--sp];
      int gy = cell / gridW;
      int gx = cell % gridW;
      if (gx < 0 || gy < 0 || gx >= gridW || gy >= gridH) {
        continue;
      }
      int idx = gy * gridW + gx;
      if (labels[idx] != 0) {
        continue;
      }
      int y = gy * step;
      int x = gx * step;
      if (y >= height || x >= width) {
        continue;
      }
      int pixelIdx = y * width + x;
      if (pixelIdx < 0 || pixelIdx >= depthData.length) {
        continue;
      }
      int depth = depthData[pixelIdx] & 0xFFFF;
      if (depth < refDepth - band || depth > refDepth + band) {
        continue;
      }
      labels[idx] = label;

      if (sp + 4 > stack.length) {
        continue;
      }
      if (gx > 0) {
        stack[sp++] = idx - 1;
      }
      if (gx + 1 < gridW) {
        stack[sp++] = idx + 1;
      }
      if (gy > 0) {
        stack[sp++] = idx - gridW;
      }
      if (gy + 1 < gridH) {
        stack[sp++] = idx + gridW;
      }
    }
  }

  private PersonBlob buildBlob(
      int label,
      short[] depthData,
      int width,
      int height,
      int step,
      int maxGroundRow,
      int gridW,
      int gridH,
      int[] labels) {
    int minX = width;
    int maxX = 0;
    int minY = height;
    int maxY = 0;
    long sumX = 0;
    long sumY = 0;
    long sumZ = 0;
    int count = 0;

    for (int gy = 0; gy < gridH; gy++) {
      int y = gy * step;
      int rowOffset = y * width;
      for (int gx = 0; gx < gridW; gx++) {
        if (labels[gy * gridW + gx] != label) {
          continue;
        }
        int x = gx * step;
        if (y >= height || x >= width) {
          continue;
        }
        int pixelIdx = rowOffset + x;
        if (pixelIdx >= depthData.length) {
          continue;
        }
        int depth = depthData[pixelIdx] & 0xFFFF;
        sumX += x;
        sumY += y;
        sumZ += depth;
        count++;
        if (x < minX) minX = x;
        if (x > maxX) maxX = x;
        if (y < minY) minY = y;
        if (y > maxY) maxY = y;
      }
    }

    if (count == 0) {
      return null;
    }

    int distanceZ = (int) (sumZ / count);
    boolean nearMode = distanceZ < NEAR_PROXIMITY_THRESHOLD_MM;
    int minPixelThreshold = nearMode ? 25 : 35;
    if (count < minPixelThreshold) {
      return null;
    }

    int bodyW = Math.max(8, maxX - minX);
    int bodyH = Math.max(12, maxY - minY);
    float aspectHW = (float) bodyH / bodyW;
    int slots = (bodyW / step + 1) * (bodyH / step + 1);
    float fillDensity = (float) count / Math.max(1, slots);
    boolean seated = (minY / (float) height) > 0.28f && aspectHW < 0.75f;

    if (!passesAnthropomorphicFilter(nearMode, seated, bodyH, height, aspectHW, fillDensity, minY)) {
      return null;
    }

    float centroidX = (float) sumX / count / width;
    float centroidY = (float) sumY / count / height;
    if (!nearMode && minY > height * 0.62f && centroidY > 0.78f) {
      return null;
    }

    return new PersonBlob(
        label,
        minX,
        maxX,
        minY,
        maxY,
        centroidX,
        centroidY,
        distanceZ,
        count,
        aspectHW,
        fillDensity,
        nearMode,
        seated);
  }

  private boolean passesAnthropomorphicFilter(
      boolean nearMode,
      boolean seated,
      int bodyH,
      int height,
      float aspectHW,
      float fillDensity,
      int minY) {
    if (nearMode) {
      float minHeight = seated ? 0.08f : 0.12f;
      float minAspect = seated ? 0.20f : 0.30f;
      return bodyH >= height * minHeight && aspectHW >= minAspect && fillDensity >= 0.18f;
    }
    return bodyH >= height * 0.18f
        && aspectHW >= 0.45f
        && minY <= height * 0.52f
        && fillDensity >= 0.22f;
  }

  private boolean isHumanDepth(short depth) {
    int d = depth & 0xFFFF;
    return d >= DepthHistogramSegmenter.MIN_DEPTH_MM && d <= DepthHistogramSegmenter.MAX_DEPTH_MM;
  }
}
