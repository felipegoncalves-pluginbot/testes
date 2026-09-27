package com.felipe.elftemplate.tracking;

/**
 * Propostas de partes do corpo no depth Astra v1 (OpenNI2, VID 0x2bc5 PID 0x0401/0x0501).
 *
 * <p>O Kinect da Microsoft (Shotton et al.) rotula cada pixel como parte do corpo e só então
 * agrupa juntas. Sem a floresta treinada, a mesma família de câmeras (PrimeSense / Xtion /
 * Orbbec Astra 1) usa o substituto aberto: mapa geodésico a partir do COM do tronco (Plagemann
 * AGEX 2010, Schwarz 2012, OpenSkeletonFitting, BodySkeletonTracker no Astra OpenNI2). Cabeça =
 * caminho curto na coluna estreita do eixo; mão = caminho longo fora dessa coluna. O pixel mais
 * alto da silhueta nunca é a cabeça se o caminho até ele for o de um braço.
 *
 * <p>Buffers pré-alocados; {@link #compute} não faz {@code new}.
 */
final class DepthGeodesicParts {

  /** Coluna da cabeça: BodySkeletonTracker usa ±afa ≈ 0,18 da largura do blob. */
  static final float HEAD_AXIS_RATIO = 0.18f;

  /**
   * Fração da geodésica máxima ainda considerada tronco/pescoço/crânio.
   *
   * <p>Schwarz: cabeça é extremo próximo do COM; punho é extremo distante.
   */
  static final float HEAD_GEODESIC_RATIO = 0.42f;

  private static final int MAX_CELLS = 4096;
  private static final int MAX_GRID = 80 * 64;
  private static final int UNREACHABLE = Integer.MAX_VALUE / 4;
  private static final int DEPTH_EDGE_MM = 90;

  private final int[] xs = new int[MAX_CELLS];
  private final int[] ys = new int[MAX_CELLS];
  private final int[] zs = new int[MAX_CELLS];
  private final int[] dist = new int[MAX_CELLS];
  private final int[] queue = new int[MAX_CELLS];
  private final int[] gridToCell = new int[MAX_GRID];

  private int cellCount;
  private int gridW;
  private int originX;
  private int originY;
  private int stepPx;
  private boolean hasHead;
  private boolean hasLeftHand;
  private boolean hasRightHand;
  private int headX;
  private int headY;
  private int headZ;
  private int leftHandX;
  private int leftHandY;
  private int leftHandZ;
  private int rightHandX;
  private int rightHandY;
  private int rightHandZ;

  boolean compute(
      short[] depth,
      int width,
      int height,
      PersonBlob blob,
      int step,
      int minZ,
      int maxZ) {
    resetOutputs();
    if (depth == null || blob == null || width <= 0 || height <= 0 || step < 1) {
      return false;
    }
    int minX = blob.minX;
    int maxX = blob.maxX;
    int minY = blob.minY;
    int maxY = blob.maxY;
    int bodyW = Math.max(8, maxX - minX);
    int bodyH = Math.max(12, maxY - minY);
    int gw = (maxX - minX) / step + 1;
    int gh = (maxY - minY) / step + 1;
    while (gw * gh > MAX_GRID && step < 32) {
      step++;
      gw = (maxX - minX) / step + 1;
      gh = (maxY - minY) / step + 1;
    }
    if (gw < 2 || gh < 2 || gw * gh > MAX_GRID) {
      return false;
    }

    fillGrid(gw * gh);
    cellCount = 0;
    originX = minX;
    originY = minY;
    stepPx = step;
    gridW = gw;
    for (int y = minY; y <= maxY && cellCount < MAX_CELLS; y += step) {
      int row = y * width;
      int gy = (y - minY) / step;
      for (int x = minX; x <= maxX && cellCount < MAX_CELLS; x += step) {
        int z = depth[row + x] & 0xFFFF;
        if (z < minZ || z > maxZ) {
          continue;
        }
        int gx = (x - minX) / step;
        int cell = cellCount;
        xs[cell] = x;
        ys[cell] = y;
        zs[cell] = z;
        dist[cell] = UNREACHABLE;
        gridToCell[gy * gw + gx] = cell;
        cellCount++;
      }
    }
    if (cellCount < 12) {
      return false;
    }

    int seed = pickTorsoSeed(blob.centroidX * width, blob.centroidY * height, bodyW, bodyH);
    if (seed < 0) {
      return false;
    }
    int maxDist = bfs(seed, gh);
    if (maxDist <= 0) {
      return false;
    }

    int axis = Math.max(step * 2, Math.round(bodyW * HEAD_AXIS_RATIO));
    int headMaxDist = Math.max(2, (int) (maxDist * HEAD_GEODESIC_RATIO));
    labelHead(blob.centroidX * width, axis, headMaxDist, bodyH);
    labelHands(blob.centroidX * width, axis, headMaxDist, minY + (int) (bodyH * 0.70f));
    return hasHead;
  }

  boolean hasHead() {
    return hasHead;
  }

  boolean hasLeftHand() {
    return hasLeftHand;
  }

  boolean hasRightHand() {
    return hasRightHand;
  }

  int headX() {
    return headX;
  }

  int headY() {
    return headY;
  }

  int headZ() {
    return headZ;
  }

  int leftHandX() {
    return leftHandX;
  }

  int leftHandY() {
    return leftHandY;
  }

  int leftHandZ() {
    return leftHandZ;
  }

  int rightHandX() {
    return rightHandX;
  }

  int rightHandY() {
    return rightHandY;
  }

  int rightHandZ() {
    return rightHandZ;
  }

  private void resetOutputs() {
    hasHead = false;
    hasLeftHand = false;
    hasRightHand = false;
    cellCount = 0;
  }

  private void fillGrid(int n) {
    int limit = Math.min(n, MAX_GRID);
    for (int i = 0; i < limit; i++) {
      gridToCell[i] = -1;
    }
  }

  private int pickTorsoSeed(float cx, float cy, int bodyW, int bodyH) {
    int axis = Math.max(2, Math.round(bodyW * HEAD_AXIS_RATIO));
    int minY = Integer.MAX_VALUE;
    for (int i = 0; i < cellCount; i++) {
      if (ys[i] < minY) {
        minY = ys[i];
      }
    }
    int y0 = minY + (int) (bodyH * 0.28f);
    int y1 = minY + (int) (bodyH * 0.58f);
    int best = -1;
    int bestD = Integer.MAX_VALUE;
    for (int i = 0; i < cellCount; i++) {
      if (ys[i] < y0 || ys[i] > y1) {
        continue;
      }
      if (Math.abs(xs[i] - cx) > axis * 1.4f) {
        continue;
      }
      int dx = xs[i] - (int) cx;
      int dy = ys[i] - (int) cy;
      int d2 = dx * dx + dy * dy;
      if (d2 < bestD) {
        bestD = d2;
        best = i;
      }
    }
    if (best >= 0) {
      return best;
    }
    for (int i = 0; i < cellCount; i++) {
      int dx = xs[i] - (int) cx;
      int dy = ys[i] - (int) cy;
      int d2 = dx * dx + dy * dy;
      if (d2 < bestD) {
        bestD = d2;
        best = i;
      }
    }
    return best;
  }

  private int bfs(int seed, int gridH) {
    dist[seed] = 0;
    int qh = 0;
    int qt = 0;
    queue[qt++] = seed;
    int maxDist = 0;
    while (qh < qt) {
      int i = queue[qh++];
      int d = dist[i];
      if (d > maxDist) {
        maxDist = d;
      }
      int gx = (xs[i] - originX) / stepPx;
      int gy = (ys[i] - originY) / stepPx;
      qt = enqueueNeighbor(i, gx - 1, gy, gridH, d, qt);
      qt = enqueueNeighbor(i, gx + 1, gy, gridH, d, qt);
      qt = enqueueNeighbor(i, gx, gy - 1, gridH, d, qt);
      qt = enqueueNeighbor(i, gx, gy + 1, gridH, d, qt);
    }
    return maxDist;
  }

  private int enqueueNeighbor(int from, int gx, int gy, int gridH, int fromDist, int qt) {
    if (gx < 0 || gy < 0 || gx >= gridW || gy >= gridH || qt >= MAX_CELLS) {
      return qt;
    }
    int j = gridToCell[gy * gridW + gx];
    if (j < 0 || dist[j] != UNREACHABLE) {
      return qt;
    }
    if (Math.abs(zs[j] - zs[from]) > DEPTH_EDGE_MM) {
      return qt;
    }
    dist[j] = fromDist + 1;
    queue[qt] = j;
    return qt + 1;
  }

  private void labelHead(float cx, int axis, int headMaxDist, int bodyH) {
    int topY = Integer.MAX_VALUE;
    for (int i = 0; i < cellCount; i++) {
      if (dist[i] == UNREACHABLE || dist[i] > headMaxDist) {
        continue;
      }
      if (Math.abs(xs[i] - cx) > axis) {
        continue;
      }
      if (ys[i] < topY) {
        topY = ys[i];
      }
    }
    if (topY == Integer.MAX_VALUE) {
      return;
    }
    int band = Math.max(stepPx * 2, bodyH / 8);
    long sumX = 0;
    long sumY = 0;
    long sumZ = 0;
    int n = 0;
    for (int i = 0; i < cellCount; i++) {
      if (dist[i] == UNREACHABLE || dist[i] > headMaxDist) {
        continue;
      }
      if (Math.abs(xs[i] - cx) > axis) {
        continue;
      }
      if (ys[i] > topY + band) {
        continue;
      }
      sumX += xs[i];
      sumY += ys[i];
      sumZ += zs[i];
      n++;
    }
    if (n < 2) {
      return;
    }
    headX = (int) (sumX / n);
    headY = (int) (sumY / n);
    headZ = (int) (sumZ / n);
    hasHead = true;
  }

  private void labelHands(float cx, int axis, int headMaxDist, int hipY) {
    int leftBest = -1;
    int rightBest = -1;
    int leftScore = -1;
    int rightScore = -1;
    for (int i = 0; i < cellCount; i++) {
      if (dist[i] == UNREACHABLE || dist[i] <= headMaxDist) {
        continue;
      }
      if (ys[i] > hipY) {
        continue;
      }
      boolean outsideAxis = Math.abs(xs[i] - cx) > axis;
      boolean aboveHead = hasHead && ys[i] < headY - stepPx;
      if (!outsideAxis && !aboveHead) {
        continue;
      }
      int lateral = Math.abs(xs[i] - (int) cx);
      int score = dist[i] * 8 + lateral;
      if (hasHead) {
        int dy = headY - ys[i];
        if (dy > 0) {
          score += dy;
        }
      }
      if (xs[i] < cx) {
        if (score > leftScore) {
          leftScore = score;
          leftBest = i;
        }
      } else if (xs[i] > cx) {
        if (score > rightScore) {
          rightScore = score;
          rightBest = i;
        }
      }
    }
    if (leftBest >= 0) {
      leftHandX = xs[leftBest];
      leftHandY = ys[leftBest];
      leftHandZ = zs[leftBest];
      hasLeftHand = true;
    }
    if (rightBest >= 0) {
      rightHandX = xs[rightBest];
      rightHandY = ys[rightBest];
      rightHandZ = zs[rightBest];
      hasRightHand = true;
    }
  }
}
