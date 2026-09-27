package com.felipe.elftemplate.tracking;

/**
 * Funde landmarks do MoveNet TFLite (RGB) com o mapa de profundidade Astra para produzir esqueleto
 * Kinect 3D.
 */
public final class PoseDepthFusion {

  public static final float POSE_CONFIDENCE_THRESHOLD = 0.35f;
  /** MoveNet model card: keypoints abaixo de ~0.3 são não confiáveis. */
  public static final int MIN_USABLE_LUMA = 24;
  public static final int MAX_USABLE_LUMA = 232;
  public static final int MIN_LUMA_SPAN = 16;
  private static final float SPATIAL_GATE = 0.40f;
  private static final float SPATIAL_GATE_LATCHED = 0.55f;
  private static final int MIN_VALID_DEPTH_MM = 500;
  private static final int MAX_VALID_DEPTH_MM = 2200;
  public static final long POSE_MAX_AGE_MS = 1000;

  private static final float[] HEAD_SCRATCH = new float[2];
  private static final float PARTIAL_SHOULDER_SPATIAL_GATE = 0.48f;

  private PoseDepthFusion() {}

  private static boolean tryResolveHead(
      PoseFrame poseFrame, float[] headOut) {
    PoseLandmarkData nose = poseFrame.getLandmark(PoseFrame.NOSE);
    if (isConfident(nose)) {
      headOut[0] = nose.x;
      headOut[1] = nose.y;
      return true;
    }
    PoseLandmarkData lEye = poseFrame.getLandmark(PoseFrame.LEFT_EYE);
    PoseLandmarkData rEye = poseFrame.getLandmark(PoseFrame.RIGHT_EYE);
    if (isConfident(lEye) && isConfident(rEye)) {
      headOut[0] = (lEye.x + rEye.x) * 0.5f;
      headOut[1] = (lEye.y + rEye.y) * 0.5f;
      return true;
    }
    if (isConfident(lEye)) {
      headOut[0] = lEye.x;
      headOut[1] = lEye.y;
      return true;
    }
    if (isConfident(rEye)) {
      headOut[0] = rEye.x;
      headOut[1] = rEye.y;
      return true;
    }
    return false;
  }

  private static void writeShoulderIfConfident(
      Joint dest,
      PoseLandmarkData lm,
      short[] depthData,
      int depthWidth,
      int depthHeight) {
    if (isConfident(lm)) {
      writeJoint(dest, lm, depthData, depthWidth, depthHeight);
    }
  }

  /**
   * MoveNet só fora da proximidade. Astra structured-light + perfil sentado (&lt;880 mm) degradam o
   * RGB; Kinect usa seated/near no depth, não um segundo pose net (Obdržálek EMBC 2012).
   */
  public static boolean allowRgbFusion(TrackingResult depthResult) {
    if (depthResult == null || !depthResult.isPlayerPresent) {
      return false;
    }
    return !depthResult.diagnostics.isNearProximityMode;
  }

  public static boolean tryFuse(
      TrackingResult result,
      PoseFrame poseFrame,
      short[] depthData,
      int depthWidth,
      int depthHeight,
      long nowMs) {
    return tryFuse(result, poseFrame, depthData, depthWidth, depthHeight, nowMs, false);
  }

  public static boolean tryFuse(
      TrackingResult result,
      PoseFrame poseFrame,
      short[] depthData,
      int depthWidth,
      int depthHeight,
      long nowMs,
      boolean alreadyFused) {

    if (result == null || poseFrame == null || poseFrame.landmarks == null || depthData == null) {
      return false;
    }
    if (nowMs - poseFrame.timestampMs > POSE_MAX_AGE_MS) {
      return false;
    }
    if (poseFrame.meanLuma >= 0
        && (poseFrame.meanLuma < MIN_USABLE_LUMA || poseFrame.meanLuma > MAX_USABLE_LUMA)) {
      return false;
    }
    if (poseFrame.lumaSpan >= 0 && poseFrame.lumaSpan < MIN_LUMA_SPAN) {
      return false;
    }

    if (!tryResolveHead(poseFrame, HEAD_SCRATCH)) {
      return false;
    }
    float headX = HEAD_SCRATCH[0];
    float headY = HEAD_SCRATCH[1];

    PoseLandmarkData screenLeftShoulder = poseFrame.getLandmark(PoseFrame.RIGHT_SHOULDER);
    PoseLandmarkData screenRightShoulder = poseFrame.getLandmark(PoseFrame.LEFT_SHOULDER);
    boolean hasLeftShoulder = isConfident(screenLeftShoulder);
    boolean hasRightShoulder = isConfident(screenRightShoulder);
    if (!hasLeftShoulder && !hasRightShoulder) {
      return false;
    }

    float spatialGate = alreadyFused ? SPATIAL_GATE_LATCHED : SPATIAL_GATE;
    if (!hasLeftShoulder || !hasRightShoulder) {
      spatialGate = Math.max(spatialGate, PARTIAL_SHOULDER_SPATIAL_GATE);
    }
    if (result.isPlayerPresent && Math.abs(headX - result.playerCentroidX) > spatialGate) {
      return false;
    }

    writeJointNorm(result.head, headX, headY, depthData, depthWidth, depthHeight);
    writeShoulderIfConfident(
        result.leftShoulder, screenLeftShoulder, depthData, depthWidth, depthHeight);
    writeShoulderIfConfident(
        result.rightShoulder, screenRightShoulder, depthData, depthWidth, depthHeight);

    float neckX;
    float neckY;
    int neckZ;
    if (hasLeftShoulder && hasRightShoulder) {
      neckX = (result.leftShoulder.x + result.rightShoulder.x) * 0.5f;
      neckY = (result.leftShoulder.y + result.rightShoulder.y) * 0.5f;
      neckZ = (result.leftShoulder.z + result.rightShoulder.z) / 2;
    } else if (hasLeftShoulder) {
      neckX = (headX + result.leftShoulder.x) * 0.5f;
      neckY = (headY + result.leftShoulder.y) * 0.5f;
      neckZ = result.leftShoulder.z;
    } else {
      neckX = (headX + result.rightShoulder.x) * 0.5f;
      neckY = (headY + result.rightShoulder.y) * 0.5f;
      neckZ = result.rightShoulder.z;
    }
    result.neck.set(neckX, neckY, neckZ);
    result.spine.set(neckX, neckY + 0.15f, neckZ);

    fuseLowerBody(result, poseFrame, depthData, depthWidth, depthHeight);
    blendHandsWithDepth(result, poseFrame, depthData, depthWidth, depthHeight);

    result.isPlayerPresent = true;
    result.playerCentroidX = result.spine.x;
    result.playerCentroidY = result.spine.y;
    result.playerDistanceZ = neckZ > 0 ? neckZ : result.playerDistanceZ;
    return true;
  }

  private static void fuseLowerBody(
      TrackingResult result,
      PoseFrame poseFrame,
      short[] depthData,
      int depthWidth,
      int depthHeight) {
    PoseLandmarkData screenLeftHip = poseFrame.getLandmark(PoseFrame.RIGHT_HIP);
    PoseLandmarkData screenRightHip = poseFrame.getLandmark(PoseFrame.LEFT_HIP);
    if (isConfident(screenLeftHip)) {
      writeJoint(result.leftHip, screenLeftHip, depthData, depthWidth, depthHeight);
    }
    if (isConfident(screenRightHip)) {
      writeJoint(result.rightHip, screenRightHip, depthData, depthWidth, depthHeight);
    }

    PoseLandmarkData screenLeftKnee = poseFrame.getLandmark(PoseFrame.RIGHT_KNEE);
    PoseLandmarkData screenRightKnee = poseFrame.getLandmark(PoseFrame.LEFT_KNEE);
    PoseLandmarkData screenLeftAnkle = poseFrame.getLandmark(PoseFrame.RIGHT_ANKLE);
    PoseLandmarkData screenRightAnkle = poseFrame.getLandmark(PoseFrame.LEFT_ANKLE);

    boolean hasLKnee = isConfident(screenLeftKnee);
    boolean hasRKnee = isConfident(screenRightKnee);
    boolean hasLAnkle = isConfident(screenLeftAnkle);
    boolean hasRAnkle = isConfident(screenRightAnkle);

    if (hasLKnee) {
      writeJoint(result.leftKnee, screenLeftKnee, depthData, depthWidth, depthHeight);
    }
    if (hasRKnee) {
      writeJoint(result.rightKnee, screenRightKnee, depthData, depthWidth, depthHeight);
    }
    if (hasLAnkle) {
      writeJoint(result.leftFoot, screenLeftAnkle, depthData, depthWidth, depthHeight);
    }
    if (hasRAnkle) {
      writeJoint(result.rightFoot, screenRightAnkle, depthData, depthWidth, depthHeight);
    }

    result.hasLegsInFrame = hasLKnee || hasRKnee;
    result.hasFeetInFrame = hasLAnkle || hasRAnkle;
  }

  private static void blendHandsWithDepth(
      TrackingResult result,
      PoseFrame poseFrame,
      short[] depthData,
      int depthWidth,
      int depthHeight) {
    float keepLeftX = result.leftHand.x;
    float keepLeftY = result.leftHand.y;
    float keepRightX = result.rightHand.x;
    float keepRightY = result.rightHand.y;
    PoseDepthFusionArms.fuseLimbs(result, poseFrame, depthData, depthWidth, depthHeight);
    float poseHandSep = Math.abs(result.leftHand.x - result.rightHand.x);
    boolean poseRaised =
        result.leftHand.y < result.leftShoulder.y - 0.04f
            || result.rightHand.y < result.rightShoulder.y - 0.04f;
    if (poseHandSep < 0.16f && !poseRaised) {
      result.leftHand.set(keepLeftX, keepLeftY, result.leftHand.z);
      result.rightHand.set(keepRightX, keepRightY, result.rightHand.z);
      result.leftHandX = keepLeftX;
      result.leftHandY = keepLeftY;
      result.rightHandX = keepRightX;
      result.rightHandY = keepRightY;
    }

    result.isLeftHandOpen =
        estimateHandOpen(depthData, depthWidth, depthHeight, result.leftHand.x, result.leftHand.y);
    result.isRightHandOpen =
        estimateHandOpen(depthData, depthWidth, depthHeight, result.rightHand.x, result.rightHand.y);
  }

  static boolean estimateHandOpen(short[] depthData, int dW, int dH, float normX, float normY) {
    if (depthData == null || dW <= 0 || dH <= 0) {
      return true;
    }
    int cx = (int) (normX * (dW - 1));
    int cy = (int) (normY * (dH - 1));
    int radius = 10;
    int count = 0;
    int minX = Math.max(0, cx - radius);
    int maxX = Math.min(dW - 1, cx + radius);
    int minY = Math.max(0, cy - radius);
    int maxY = Math.min(dH - 1, cy + radius);
    for (int y = minY; y <= maxY; y += 2) {
      int rowOffset = y * dW;
      for (int x = minX; x <= maxX; x += 2) {
        int d = depthData[rowOffset + x] & 0xFFFF;
        if (d >= MIN_VALID_DEPTH_MM && d <= MAX_VALID_DEPTH_MM) {
          count++;
        }
      }
    }
    return count >= 12;
  }

  static void writeJoint(Joint dest, PoseLandmarkData lm, short[] depthData, int dW, int dH) {
    if (dest == null || lm == null) {
      return;
    }
    writeJointNorm(dest, lm.x, lm.y, depthData, dW, dH);
  }

  static void writeJointNorm(Joint dest, float x, float y, short[] depthData, int dW, int dH) {
    if (dest == null) {
      return;
    }
    float normX = Math.max(0.0f, Math.min(1.0f, x));
    float normY = Math.max(0.0f, Math.min(1.0f, y));
    dest.set(normX, normY, sampleDepthAtNorm(depthData, dW, dH, normX, normY));
  }

  static int sampleDepthAtNorm(short[] depthData, int dW, int dH, float normX, float normY) {
    int px = (int) (normX * (dW - 1));
    int py = (int) (normY * (dH - 1));
    int idx = py * dW + px;
    if (idx >= 0 && idx < depthData.length) {
      int d = depthData[idx] & 0xFFFF;
      if (d >= MIN_VALID_DEPTH_MM && d <= MAX_VALID_DEPTH_MM) {
        return d;
      }
    }
    return 0;
  }

  public static int sampleDepthMm(
      int rgbX, int rgbY, short[] depthData, int dW, int dH, int rgbW, int rgbH) {
    float normX = (float) rgbX / Math.max(1, rgbW);
    float normY = (float) rgbY / Math.max(1, rgbH);
    return sampleDepthAtNorm(depthData, dW, dH, normX, normY);
  }

  public static boolean isConfident(PoseLandmarkData landmark) {
    return landmark != null && landmark.score >= POSE_CONFIDENCE_THRESHOLD;
  }
}
