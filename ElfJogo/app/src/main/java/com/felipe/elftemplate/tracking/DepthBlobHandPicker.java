package com.felipe.elftemplate.tracking;

/**
 * Punho = extrema lateral (NiTE/AGEX: ponta do membro, não o pixel mais baixo).
 * Logcat Elf: pick=maxY com abovePx&lt;0 colocava a mão no quadril e ignorava braço erguido.
 */
final class DepthBlobHandPicker {

  private static final int RAISE_ABOVE_PX = 6;

  private DepthBlobHandPicker() {}

  static float pickNormY(
      DepthBlobLimbScanner.LimbScan scan, float shoulderY, int height, boolean left) {
    return pickNormY(scan, shoulderY, height, left, false);
  }

  static float pickNormY(
      DepthBlobLimbScanner.LimbScan scan,
      float shoulderY,
      int height,
      boolean left,
      boolean nearOrSeated) {
    int shoulderPixelY = (int) (shoulderY * height);
    int minY = left ? scan.leftMinY : scan.rightMinY;
    int maxY = left ? scan.leftMaxY : scan.rightMaxY;
    int extremityY = left ? scan.leftExtremityY : scan.rightExtremityY;
    int abovePx = minY != Integer.MAX_VALUE ? shoulderPixelY - minY : 0;
    boolean furnitureColumn =
        nearOrSeated
            && minY != Integer.MAX_VALUE
            && maxY > minY
            && (maxY - minY) > height * 0.42f;

    int handPixelY;
    if (!furnitureColumn && abovePx >= RAISE_ABOVE_PX && minY != Integer.MAX_VALUE) {
      boolean extremityIsHigh =
          extremityY > 0 && extremityY <= minY + Math.max(8, height / 10);
      handPixelY = extremityIsHigh ? extremityY : minY;
    } else if (extremityY > 0) {
      int dropMax = shoulderPixelY + (int) (0.22f * height);
      int dropMin = shoulderPixelY + (int) (0.05f * height);
      handPixelY = extremityY;
      if (handPixelY > dropMax) {
        handPixelY = dropMax;
      }
      if (handPixelY < dropMin) {
        handPixelY = dropMin;
      }
    } else {
      handPixelY =
          shoulderPixelY + (int) (DepthBlobAnatomy.HAND_FALLBACK_SHOULDER_DROP_Y * height);
    }
    return handPixelY / (float) height;
  }

  static float pickInnerNormY(
      DepthBlobLimbScanner.LimbScan scan, float shoulderY, int height, boolean left) {
    int shoulderPixelY = (int) (shoulderY * height);
    int minY = left ? scan.innerLeftMinY : scan.innerRightMinY;
    int maxY = left ? scan.innerLeftMaxY : scan.innerRightMaxY;
    int abovePx = minY != Integer.MAX_VALUE ? shoulderPixelY - minY : 0;

    int handPixelY;
    if (abovePx >= RAISE_ABOVE_PX && minY != Integer.MAX_VALUE) {
      handPixelY = minY;
    } else if (maxY > 0) {
      int dropMax = shoulderPixelY + (int) (0.22f * height);
      handPixelY = Math.min(maxY, dropMax);
    } else {
      handPixelY =
          shoulderPixelY + (int) (DepthBlobAnatomy.HAND_FALLBACK_SHOULDER_DROP_Y * height);
    }
    return handPixelY / (float) height;
  }
}
