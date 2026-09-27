package com.felipe.elftemplate.tracking;

/** Varredura de cabeça e braços em um blob de profundidade. */
final class DepthBlobLimbScanner {

  static LimbScan scan(
      short[] depthData,
      int width,
      int minX,
      int maxX,
      int minY,
      int maxY,
      int step,
      int minZ,
      int maxZ,
      int minCentralY,
      int headCutoffY,
      int raiseMinY,
      float centroidX,
      int bodyW,
      float torsoMargin,
      float armLateralMin,
      int armRowStartY,
      int armRowEndY) {
    LimbScan s = new LimbScan();
    int cx = (int) centroidX;
    for (int y = minY; y <= maxY; y += step) {
      int rowOffset = y * width;
      boolean headZone = y >= minCentralY && y <= headCutoffY;
      boolean armRowZone = y >= armRowStartY && y <= armRowEndY;
      boolean raiseZone = y >= raiseMinY && y < headCutoffY;
      for (int x = minX; x <= maxX; x += step) {
        int depth = depthData[rowOffset + x] & 0xFFFF;
        if (depth < minZ || depth > maxZ) {
          continue;
        }
        if (headZone && Math.abs(x - centroidX) <= bodyW * 0.28f) {
          s.headSumX += x;
          s.headSumY += y;
          s.headCount++;
        }
        if (armRowZone && x < centroidX && x >= centroidX - torsoMargin) {
          s.innerLeftCount++;
          if (y < s.innerLeftMinY) {
            s.innerLeftMinY = y;
          }
          if (y > s.innerLeftMaxY) {
            s.innerLeftMaxY = y;
          }
        }
        if (armRowZone && x > centroidX && x <= centroidX + torsoMargin) {
          s.innerRightCount++;
          if (y < s.innerRightMinY) {
            s.innerRightMinY = y;
          }
          if (y > s.innerRightMaxY) {
            s.innerRightMaxY = y;
          }
        }
        if (x < centroidX - torsoMargin) {
          if (raiseZone) {
            noteRaise(s, true, x, y, cx, headCutoffY);
          }
          if (armRowZone && y >= headCutoffY) {
            s.leftCount++;
            if (x < s.leftMinX) {
              s.leftMinX = x;
              s.leftExtremityY = y;
            }
            if (y < s.leftMinY) {
              s.leftMinY = y;
              s.leftHighestX = x;
            }
            if (y > s.leftMaxY) {
              s.leftMaxY = y;
            }
          }
        }
        if (x > centroidX + torsoMargin) {
          if (raiseZone) {
            noteRaise(s, false, x, y, cx, headCutoffY);
          }
          if (armRowZone && y >= headCutoffY) {
            s.rightCount++;
            if (x > s.rightMaxX) {
              s.rightMaxX = x;
              s.rightExtremityY = y;
            }
            if (y < s.rightMinY) {
              s.rightMinY = y;
              s.rightHighestX = x;
            }
            if (y > s.rightMaxY) {
              s.rightMaxY = y;
            }
          }
        }
      }
    }
    return s;
  }

  /** NiTE: ponta do membro = pixel do raise zone mais longe do ombro, um só ponto. */
  private static void noteRaise(LimbScan s, boolean left, int x, int y, int cx, int headCutoffY) {
    int dx = x - cx;
    int dy = y - headCutoffY;
    int d2 = dx * dx + dy * dy;
    if (left) {
      s.leftRaiseCount++;
      if (d2 >= s.leftRaiseD2) {
        s.leftRaiseD2 = d2;
        s.leftRaiseHandX = x;
        s.leftRaiseHandY = y;
      }
    } else {
      s.rightRaiseCount++;
      if (d2 >= s.rightRaiseD2) {
        s.rightRaiseD2 = d2;
        s.rightRaiseHandX = x;
        s.rightRaiseHandY = y;
      }
    }
  }

  static final class LimbScan {
    long headSumX = 0;
    long headSumY = 0;
    int headCount = 0;
    int leftMinX = Integer.MAX_VALUE;
    int leftMinY = Integer.MAX_VALUE;
    int leftMaxY = 0;
    int leftExtremityY = 0;
    int leftHighestX = 0;
    int leftCount = 0;
    int rightMaxX = 0;
    int rightMinY = Integer.MAX_VALUE;
    int rightMaxY = 0;
    int rightExtremityY = 0;
    int rightHighestX = 0;
    int rightCount = 0;
    int innerLeftCount = 0;
    int innerLeftMinY = Integer.MAX_VALUE;
    int innerLeftMaxY = 0;
    int innerRightCount = 0;
    int innerRightMinY = Integer.MAX_VALUE;
    int innerRightMaxY = 0;
    int leftRaiseCount = 0;
    int leftRaiseHandX = Integer.MAX_VALUE;
    int leftRaiseHandY = Integer.MAX_VALUE;
    int leftRaiseD2 = -1;
    int rightRaiseCount = 0;
    int rightRaiseHandX = 0;
    int rightRaiseHandY = Integer.MAX_VALUE;
    int rightRaiseD2 = -1;
  }
}
