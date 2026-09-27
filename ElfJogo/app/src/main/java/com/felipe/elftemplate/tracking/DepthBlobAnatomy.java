package com.felipe.elftemplate.tracking;

/** Extrai cabeça, braços e pernas de um blob de profundidade já selecionado. */
final class DepthBlobAnatomy {

  /** Mínimo de amostras laterais no scan (step ~10px em 640w) para confiar no extremo do braço. */
  private static final int MIN_ARM_PIXEL_SAMPLES = 3;

  /** Queda vertical do braço no fallback (ombro → punho estimado), não quadril. */
  static final float HAND_FALLBACK_SHOULDER_DROP_Y = 0.12f;

  private DepthBlobAnatomy() {}

  static void fill(
      short[] depthData,
      int width,
      int height,
      PersonBlob blob,
      TrackingResult result,
      KinectGestureClassifier gestureClassifier) {

    int step = Math.max(1, width / 64);
    int maxGroundRow = (int) (height * 0.90f);
    int bodyW = Math.max(8, blob.maxX - blob.minX);
    int bodyH = Math.max(12, blob.maxY - blob.minY);

    int sliceMargin = blob.nearMode ? 260 : 360;
    int minPlayerZ = Math.max(DepthHistogramSegmenter.MIN_DEPTH_MM, blob.distanceZ - sliceMargin);
    int maxPlayerZ = Math.min(DepthHistogramSegmenter.MAX_DEPTH_MM, blob.distanceZ + sliceMargin);

    result.diagnostics.sliceMinZ = minPlayerZ;
    result.diagnostics.sliceMaxZ = maxPlayerZ;
    result.diagnostics.validPixelCount = blob.pixelCount;
    result.diagnostics.bodyAspectHW = blob.aspectHW;
    result.diagnostics.isSeatedPose = blob.seatedPose;
    result.diagnostics.isNearProximityMode = blob.nearMode;
    float centroidPixelX = blob.centroidX * width;
    float torsoMarginX = Math.max(bodyW * 0.32f, step * 2.0f);
    float headColumnMarginX = Math.max(step * 2.0f, bodyW * 0.18f);

    int minCentralY =
        findHeadTop(
            depthData, width, blob.minX, blob.maxX, blob.minY, blob.maxY, step,
            minPlayerZ, maxPlayerZ, centroidPixelX, headColumnMarginX, bodyW, blob.maxY);

    int headCutoffY = minCentralY + (int) (bodyH * (blob.nearMode ? 0.32f : 0.25f));
    float shoulderSpanPx = Math.max(width * 0.12f, bodyW * 0.38f);
    float armLateralMin = Math.max(torsoMarginX * 1.35f, shoulderSpanPx * 0.85f);

    int armRowStartY = Math.max(blob.minY, minCentralY);
    int hipRow = blob.minY + (int) (bodyH * 0.62f);
    int armRowEndY = Math.max(armRowStartY + step, Math.min(blob.maxY - step * 2, hipRow));
    // Teto/lâmpada acima da cabeça (viewer y≈0.09) não entra na zona de raise.
    int raiseMinY = Math.max(blob.minY, minCentralY - Math.max(step * 4, height / 5));

    DepthBlobLimbScanner.LimbScan scan =
        DepthBlobLimbScanner.scan(
            depthData, width, blob.minX, blob.maxX, blob.minY, blob.maxY, step,
            minPlayerZ, maxPlayerZ, minCentralY, headCutoffY, raiseMinY, centroidPixelX, bodyW,
            torsoMarginX, armLateralMin, armRowStartY, armRowEndY);

    if (!blob.nearMode && scan.headCount < 3) {
      return;
    }

    applySkeleton(
        result, blob, width, height, bodyW, bodyH, blob.minY, blob.maxY, maxGroundRow, step,
        scan, centroidPixelX, torsoMarginX, gestureClassifier);
  }

  private static int findHeadTop(
      short[] depthData, int width, int minX, int maxX, int minY, int maxY, int step,
      int minZ, int maxZ, float centroidX, float torsoMargin, int bodyW, int defaultY) {
    int minCentralY = defaultY;
    for (int y = minY; y <= maxY; y += step) {
      int rowOffset = y * width;
      for (int x = minX; x <= maxX; x += step) {
        int depth = depthData[rowOffset + x] & 0xFFFF;
        if (depth >= minZ && depth <= maxZ
            && Math.abs(x - centroidX) <= Math.max(torsoMargin, bodyW * 0.30f)) {
          if (y < minCentralY) {
            minCentralY = y;
            break;
          }
        }
      }
      if (minCentralY < defaultY) {
        break;
      }
    }
    return minCentralY;
  }

  private static void applySkeleton(
      TrackingResult result, PersonBlob blob, int width, int height, int bodyW, int bodyH,
      int minY, int maxY, int maxGroundRow, int step, DepthBlobLimbScanner.LimbScan scan,
      float centroidPixelX, float torsoMarginX, KinectGestureClassifier gestureClassifier) {

    result.isPlayerPresent = true;
    result.playerCentroidX = blob.centroidX;
    result.playerCentroidY = blob.centroidY;
    result.playerDistanceZ = blob.distanceZ;
    result.diagnostics.headPixelCount = scan.headCount;
    result.diagnostics.leftHandPixelCount = scan.leftCount;
    result.diagnostics.rightHandPixelCount = scan.rightCount;

    float headX = scan.headCount > 3 ? (float) scan.headSumX / scan.headCount / width : result.playerCentroidX;
    float headY = scan.headCount > 3 ? (float) scan.headSumY / scan.headCount / height : minY / (float) height + 0.05f;
    result.head.set(headX, headY, result.playerDistanceZ);

    float neckY = headY + (bodyH / (float) height) * 0.12f;
    result.neck.set(headX, neckY, result.playerDistanceZ);
    result.spine.set(result.playerCentroidX, result.playerCentroidY, result.playerDistanceZ);

    float shoulderSpan = Math.max(0.10f, (bodyW / (float) width) * 0.35f);
    float shoulderY = neckY + 0.03f;
    result.leftShoulder.set(
        Math.max(0.04f, result.playerCentroidX - shoulderSpan), shoulderY, result.playerDistanceZ);
    result.rightShoulder.set(
        Math.min(0.96f, result.playerCentroidX + shoulderSpan), shoulderY, result.playerDistanceZ);

    applyHand(result, scan, shoulderY, width, height, centroidPixelX, torsoMarginX, true, gestureClassifier);
    applyHand(result, scan, shoulderY, width, height, centroidPixelX, torsoMarginX, false, gestureClassifier);

    result.leftElbow.set(
        (result.leftShoulder.x + result.leftHand.x) * 0.5f,
        (result.leftShoulder.y + result.leftHand.y) * 0.5f,
        result.playerDistanceZ);
    result.rightElbow.set(
        (result.rightShoulder.x + result.rightHand.x) * 0.5f,
        (result.rightShoulder.y + result.rightHand.y) * 0.5f,
        result.playerDistanceZ);

    boolean touchesBottom = maxY >= maxGroundRow - step * 2;
    boolean hasLegHeight = bodyH >= height * 0.45f && !blob.nearMode;
    result.hasFeetInFrame = !touchesBottom && hasLegHeight;
    result.hasLegsInFrame = !blob.nearMode && bodyH >= height * 0.35f;

    float hipY = result.playerCentroidY + (bodyH / (float) height) * 0.24f;
    result.leftHip.set(
        result.playerCentroidX - shoulderSpan * 0.50f, hipY, result.playerDistanceZ);
    result.rightHip.set(
        result.playerCentroidX + shoulderSpan * 0.50f, hipY, result.playerDistanceZ);

    if (result.hasFeetInFrame) {
      float footY = Math.min(0.96f, maxY / (float) height);
      result.leftFoot.set(result.leftHip.x, footY, result.playerDistanceZ);
      result.rightFoot.set(result.rightHip.x, footY, result.playerDistanceZ);
    } else {
      result.leftFoot.set(result.leftHip.x, hipY + 0.05f, result.playerDistanceZ);
      result.rightFoot.set(result.rightHip.x, hipY + 0.05f, result.playerDistanceZ);
    }

    clampHandNotBelowHip(result.leftHand, result.leftHip, result.leftHandX, true, result);
    clampHandNotBelowHip(result.rightHand, result.rightHip, result.rightHandX, false, result);
    result.leftElbow.set(
        (result.leftShoulder.x + result.leftHand.x) * 0.5f,
        (result.leftShoulder.y + result.leftHand.y) * 0.5f,
        result.playerDistanceZ);
    result.rightElbow.set(
        (result.rightShoulder.x + result.rightHand.x) * 0.5f,
        (result.rightShoulder.y + result.rightHand.y) * 0.5f,
        result.playerDistanceZ);
  }

  private static void clampHandNotBelowHip(
      Joint hand, Joint hip, float handX, boolean left, TrackingResult result) {
    if (hand == null || hip == null) {
      return;
    }
    float maxY = hip.y + 0.04f;
    if (hand.y > maxY) {
      hand.set(hand.x, maxY, hand.z);
      if (left) {
        result.leftHandY = maxY;
        result.leftHandX = handX;
      } else {
        result.rightHandY = maxY;
        result.rightHandX = handX;
      }
    }
  }

  private static void applyHand(
      TrackingResult result, DepthBlobLimbScanner.LimbScan scan, float shoulderY, int width, int height,
      float centroidPixelX, float torsoMarginX, boolean left, KinectGestureClassifier gestureClassifier) {
    if (left) {
      applyLeftHand(result, scan, shoulderY, width, height, centroidPixelX, torsoMarginX, gestureClassifier);
    } else {
      applyRightHand(result, scan, shoulderY, width, height, centroidPixelX, torsoMarginX, gestureClassifier);
    }
  }

  private static void applyLeftHand(
      TrackingResult result, DepthBlobLimbScanner.LimbScan scan, float shoulderY, int width, int height,
      float centroidPixelX, float torsoMarginX, KinectGestureClassifier gestureClassifier) {
    if (scan.leftRaiseCount >= MIN_ARM_PIXEL_SAMPLES
        && scan.leftRaiseCount >= scan.leftCount) {
      float handY = scan.leftRaiseHandY / (float) height;
      if (isRaiseHandAboveShoulder(handY, shoulderY) && isPlausibleRaise(result, handY)) {
      float rawHandX = scan.leftRaiseHandX / (float) width;
      float maxReach = Math.max(0.12f, Math.abs(result.leftShoulder.x - result.playerCentroidX) * 2.2f);
      float minValidX = Math.max(0.04f, result.leftShoulder.x - maxReach);
      float clampedHandX = Math.max(minValidX, Math.min(result.leftShoulder.x + 0.05f, rawHandX));
      boolean separated = Math.abs(clampedHandX * width - centroidPixelX) >= torsoMarginX;
      assignLeftHand(result, clampedHandX, handY, shoulderY, separated, gestureClassifier,
          "left_hand_raise", scan.leftRaiseCount, 0, false);
      }
    } else if (scan.leftCount >= MIN_ARM_PIXEL_SAMPLES) {
      boolean nearOrSeated =
          result.diagnostics.isNearProximityMode || result.diagnostics.isSeatedPose;
      float handY = DepthBlobHandPicker.pickNormY(scan, shoulderY, height, true, nearOrSeated);
      float rawHandX = scan.leftMinX / (float) width;
      // Clamp antropomórfico: extensão máxima do braço esquerdo em relação ao ombro
      float maxReach = Math.max(0.12f, Math.abs(result.leftShoulder.x - result.playerCentroidX) * 2.2f);
      float minValidX = Math.max(0.04f, result.leftShoulder.x - maxReach);
      float clampedHandX = Math.max(minValidX, Math.min(result.leftShoulder.x + 0.05f, rawHandX));
      boolean separated = Math.abs(clampedHandX * width - centroidPixelX) >= torsoMarginX;
      assignLeftHand(result, clampedHandX, handY, shoulderY, separated, gestureClassifier,
          "left_hand_blob", scan.leftCount, 0, false);
    } else if (scan.innerLeftCount >= MIN_ARM_PIXEL_SAMPLES) {
      float handY = DepthBlobHandPicker.pickInnerNormY(scan, shoulderY, height, true);
      assignLeftHand(result, result.leftShoulder.x - 0.04f, handY, shoulderY, false, gestureClassifier,
          "left_hand_inner", scan.leftCount, scan.innerLeftCount, false);
    } else {
      gestureClassifier.markLeftArmMissing();
      result.leftHandX = result.leftShoulder.x - 0.06f;
      result.leftHandY = Math.min(0.95f, shoulderY + HAND_FALLBACK_SHOULDER_DROP_Y);
      result.isLeftHandRaised = gestureClassifier.isLeftArmRaised();
      result.leftHandElevation = gestureClassifier.getLeftArmElevation();
      result.leftHand.set(result.leftHandX, result.leftHandY, result.playerDistanceZ);
      DebugTrace.log("A", "DepthBlobAnatomy.applyHand", "left_hand_fallback_shoulder",
          "{\"leftCount\":" + scan.leftCount + ",\"innerCount\":" + scan.innerLeftCount
              + ",\"handY\":" + result.leftHandY + ",\"fallback\":true}");
    }
  }

  private static void applyRightHand(
      TrackingResult result, DepthBlobLimbScanner.LimbScan scan, float shoulderY, int width, int height,
      float centroidPixelX, float torsoMarginX, KinectGestureClassifier gestureClassifier) {
    if (scan.rightRaiseCount >= MIN_ARM_PIXEL_SAMPLES
        && scan.rightRaiseCount >= scan.rightCount) {
      float handY = scan.rightRaiseHandY / (float) height;
      if (isRaiseHandAboveShoulder(handY, shoulderY) && isPlausibleRaise(result, handY)) {
      float rawHandX = scan.rightRaiseHandX / (float) width;
      float maxReach = Math.max(0.12f, Math.abs(result.rightShoulder.x - result.playerCentroidX) * 2.2f);
      float maxValidX = Math.min(0.96f, result.rightShoulder.x + maxReach);
      float clampedHandX = Math.min(maxValidX, Math.max(result.rightShoulder.x - 0.05f, rawHandX));
      boolean separated = Math.abs(clampedHandX * width - centroidPixelX) >= torsoMarginX;
      assignRightHand(result, clampedHandX, handY, shoulderY, separated, gestureClassifier,
          "right_hand_raise", scan.rightRaiseCount, 0, false);
      }
    } else if (scan.rightCount >= MIN_ARM_PIXEL_SAMPLES) {
      boolean nearOrSeated =
          result.diagnostics.isNearProximityMode || result.diagnostics.isSeatedPose;
      float handY = DepthBlobHandPicker.pickNormY(scan, shoulderY, height, false, nearOrSeated);
      float rawHandX = scan.rightMaxX / (float) width;
      // Clamp antropomórfico: extensão máxima do braço direito em relação ao ombro
      float maxReach = Math.max(0.12f, Math.abs(result.rightShoulder.x - result.playerCentroidX) * 2.2f);
      float maxValidX = Math.min(0.96f, result.rightShoulder.x + maxReach);
      float clampedHandX = Math.min(maxValidX, Math.max(result.rightShoulder.x - 0.05f, rawHandX));
      boolean separated = Math.abs(clampedHandX * width - centroidPixelX) >= torsoMarginX;
      assignRightHand(result, clampedHandX, handY, shoulderY, separated, gestureClassifier,
          "right_hand_blob", scan.rightCount, 0, false);
    } else if (scan.innerRightCount >= MIN_ARM_PIXEL_SAMPLES) {
      float handY = DepthBlobHandPicker.pickInnerNormY(scan, shoulderY, height, false);
      assignRightHand(result, result.rightShoulder.x + 0.04f, handY, shoulderY, false, gestureClassifier,
          "right_hand_inner", scan.rightCount, scan.innerRightCount, false);
    } else {
      gestureClassifier.markRightArmMissing();
      result.rightHandX = result.rightShoulder.x + 0.06f;
      result.rightHandY = Math.min(0.95f, shoulderY + HAND_FALLBACK_SHOULDER_DROP_Y);
      result.isRightHandRaised = gestureClassifier.isRightArmRaised();
      result.rightHandElevation = gestureClassifier.getRightArmElevation();
      result.rightHand.set(result.rightHandX, result.rightHandY, result.playerDistanceZ);
      DebugTrace.log("A", "DepthBlobAnatomy.applyHand", "right_hand_fallback_shoulder",
          "{\"rightCount\":" + scan.rightCount + ",\"innerCount\":" + scan.innerRightCount
              + ",\"handY\":" + result.rightHandY + ",\"fallback\":true}");
    }
  }

  /** Mão precisa estar visivelmente acima do ombro (y menor); ombros na raise zone geravam falso HANDS_UP. */
  private static final float RAISE_ABOVE_SHOULDER_Y = 0.04f;

  private static boolean isRaiseHandAboveShoulder(float handYNorm, float shoulderYNorm) {
    return handYNorm < shoulderYNorm - RAISE_ABOVE_SHOULDER_Y;
  }

  /** HANDS_UP e braço a 45° no fixture cabem em ≤0.26 acima da cabeça. Teto do quadro passa disso. */
  static boolean isPlausibleRaise(TrackingResult result, float handY) {
    if (result.head == null) {
      return true;
    }
    float above = result.head.y - handY;
    if (above <= 0f) {
      return true;
    }
    return above <= 0.26f;
  }

  private static void assignLeftHand(
      TrackingResult result, float handX, float handY, float shoulderY, boolean separated,
      KinectGestureClassifier gestureClassifier, String logMsg, int lateralPx, int innerPx,
      boolean fallback) {
    boolean raised = gestureClassifier.updateLeftArmState(handY, shoulderY, separated);
    result.isLeftHandRaised = raised;
    result.leftHandElevation = gestureClassifier.getLeftArmElevation();
    result.leftHandX = handX;
    result.leftHandY = handY;
    result.leftHand.set(handX, handY, result.playerDistanceZ);
    result.diagnostics.leftHandPixelCount = lateralPx > 0 ? lateralPx : innerPx;
    DebugTrace.log("A", "DepthBlobAnatomy.applyHand", logMsg,
        "{\"lateralPx\":" + lateralPx + ",\"innerPx\":" + innerPx + ",\"handY\":" + handY
            + ",\"elev\":" + gestureClassifier.getLeftArmElevation() + ",\"fallback\":" + fallback);
  }

  private static void assignRightHand(
      TrackingResult result, float handX, float handY, float shoulderY, boolean separated,
      KinectGestureClassifier gestureClassifier, String logMsg, int lateralPx, int innerPx,
      boolean fallback) {
    boolean raised = gestureClassifier.updateRightArmState(handY, shoulderY, separated);
    result.isRightHandRaised = raised;
    result.rightHandElevation = gestureClassifier.getRightArmElevation();
    result.rightHandX = handX;
    result.rightHandY = handY;
    result.rightHand.set(handX, handY, result.playerDistanceZ);
    result.diagnostics.rightHandPixelCount = lateralPx > 0 ? lateralPx : innerPx;
    DebugTrace.log("A", "DepthBlobAnatomy.applyHand", logMsg,
        "{\"lateralPx\":" + lateralPx + ",\"innerPx\":" + innerPx + ",\"handY\":" + handY
            + ",\"elev\":" + gestureClassifier.getRightArmElevation() + ",\"fallback\":" + fallback);
  }
}
