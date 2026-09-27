package com.felipe.elftemplate.tracking;

/**
 * Holt double-exponential smoothing 2D nos punhos (Kinect SDK NUI_TRANSFORM_SMOOTH_PARAMETERS).
 * Reduz jitter e limita saltos espúrios em X e Y (fMaxDeviationRadius e fMaxStepPerFrame).
 */
final class KinectHoltHandFilter {

  private static final float SMOOTHING = 0.55f;
  private static final float CORRECTION = 0.45f;
  private static final float PREDICTION = 0.15f;
  private static final float JITTER_RADIUS_Y = 0.016f;
  private static final float JITTER_RADIUS_X = 0.012f;
  private static final float MAX_DEVIATION_Y = 0.11f;
  private static final float MAX_DEVIATION_X = 0.09f;
  private static final float MAX_STEP_PER_FRAME_Y = 0.085f;
  private static final float MAX_STEP_PER_FRAME_X = 0.070f;

  private float filteredLeftY = 0.55f;
  private float trendLeftY = 0f;
  private float filteredRightY = 0.55f;
  private float trendRightY = 0f;

  private float filteredLeftX = 0.35f;
  private float trendLeftX = 0f;
  private float filteredRightX = 0.65f;
  private float trendRightX = 0f;

  private boolean leftInitY = false;
  private boolean rightInitY = false;
  private boolean leftInitX = false;
  private boolean rightInitX = false;

  void filterHands(TrackingResult result) {
    if (!result.isPlayerPresent) {
      leftInitY = false;
      rightInitY = false;
      leftInitX = false;
      rightInitX = false;
      return;
    }
    result.leftHand.y = clamp01(filterSideY(true, result.leftHand.y));
    result.rightHand.y = clamp01(filterSideY(false, result.rightHand.y));
    result.leftHand.x = clamp01(filterSideX(true, result.leftHand.x));
    result.rightHand.x = clamp01(filterSideX(false, result.rightHand.x));

    result.leftHandX = result.leftHand.x;
    result.rightHandX = result.rightHand.x;
    result.leftHandY = result.leftHand.y;
    result.rightHandY = result.rightHand.y;
  }

  private float filterSideY(boolean left, float rawY) {
    if (!left && !rightInitY) {
      rightInitY = true;
      filteredRightY = rawY;
      trendRightY = 0f;
      return rawY;
    }
    if (left && !leftInitY) {
      leftInitY = true;
      filteredLeftY = rawY;
      trendLeftY = 0f;
      return rawY;
    }

    float filtered = left ? filteredLeftY : filteredRightY;
    float trend = left ? trendLeftY : trendRightY;

    float prevFiltered = filtered;
    filtered = filtered + CORRECTION * (rawY - filtered);
    trend = trend + SMOOTHING * (filtered - prevFiltered);
    float predicted = filtered + PREDICTION * trend;

    float diff = predicted - rawY;
    if (Math.abs(diff) > MAX_DEVIATION_Y) {
      predicted = rawY + (diff > 0 ? MAX_DEVIATION_Y : -MAX_DEVIATION_Y);
    }
    if (Math.abs(predicted - filtered) < JITTER_RADIUS_Y) {
      predicted = filtered;
    }

    float step = predicted - prevFiltered;
    if (Math.abs(step) > MAX_STEP_PER_FRAME_Y) {
      predicted = prevFiltered + (step > 0 ? MAX_STEP_PER_FRAME_Y : -MAX_STEP_PER_FRAME_Y);
    }

    if (left) {
      filteredLeftY = predicted;
      trendLeftY = trend;
      return predicted;
    }
    filteredRightY = predicted;
    trendRightY = trend;
    return predicted;
  }

  private float filterSideX(boolean left, float rawX) {
    if (!left && !rightInitX) {
      rightInitX = true;
      filteredRightX = rawX;
      trendRightX = 0f;
      return rawX;
    }
    if (left && !leftInitX) {
      leftInitX = true;
      filteredLeftX = rawX;
      trendLeftX = 0f;
      return rawX;
    }

    float filtered = left ? filteredLeftX : filteredRightX;
    float trend = left ? trendLeftX : trendRightX;

    float prevFiltered = filtered;
    filtered = filtered + CORRECTION * (rawX - filtered);
    trend = trend + SMOOTHING * (filtered - prevFiltered);
    float predicted = filtered + PREDICTION * trend;

    float diff = predicted - rawX;
    if (Math.abs(diff) > MAX_DEVIATION_X) {
      predicted = rawX + (diff > 0 ? MAX_DEVIATION_X : -MAX_DEVIATION_X);
    }
    if (Math.abs(predicted - filtered) < JITTER_RADIUS_X) {
      predicted = filtered;
    }

    float step = predicted - prevFiltered;
    if (Math.abs(step) > MAX_STEP_PER_FRAME_X) {
      predicted = prevFiltered + (step > 0 ? MAX_STEP_PER_FRAME_X : -MAX_STEP_PER_FRAME_X);
    }

    if (left) {
      filteredLeftX = predicted;
      trendLeftX = trend;
      return predicted;
    }
    filteredRightX = predicted;
    trendRightX = trend;
    return predicted;
  }

  /** Contrato COORDINATE_FRAMES: X/Y ∈ [0, 1]. Holt não pode empurrar o punho para fora do quadro. */
  static float clamp01(float v) {
    if (v < 0f) {
      return 0f;
    }
    if (v > 1f) {
      return 1f;
    }
    return v;
  }
}
