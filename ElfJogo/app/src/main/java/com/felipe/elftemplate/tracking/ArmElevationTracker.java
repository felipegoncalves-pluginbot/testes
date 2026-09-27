package com.felipe.elftemplate.tracking;

/**
 * Elevação de braço 0..1 com EMA + Schmitt + dwell — reduz oscilação LEVANTADA/ABAIXADA em depth blob.
 * Referência: histerese temporal (US9182838), suavização Kalman/EMA em tracking depth (CVPR/PMC).
 */
final class ArmElevationTracker {

  static final float EMA_ALPHA = 0.38f;
  static final float RAISE_ENTER_ELEV = 0.35f;
  static final float RAISE_EXIT_ELEV = 0.20f;
  static final long MIN_TOGGLE_MS = 200L;
  static final float FORWARD_REACH_SCALE = 0.72f;
  static final float FORWARD_REACH_CAP = 0.62f;
  static final float DECAY_FACTOR = 0.82f;

  private float smoothedElevation = 0f;
  private boolean raised = false;
  private long lastToggleMs = 0L;

  float update(float shoulderY, float handY, boolean handVisible, boolean lateralSeparated) {
    if (!handVisible) {
      smoothedElevation *= DECAY_FACTOR;
      if (smoothedElevation < 0.06f) {
        raised = false;
        smoothedElevation = 0f;
      }
      return smoothedElevation;
    }

    float raw = computeRawElevation(shoulderY, handY);
    if (!lateralSeparated) {
      raw *= FORWARD_REACH_SCALE;
      if (raw > FORWARD_REACH_CAP) {
        raw = FORWARD_REACH_CAP;
      }
    }
    smoothedElevation = (EMA_ALPHA * raw) + ((1.0f - EMA_ALPHA) * smoothedElevation);
    if (raw < 0.10f && smoothedElevation > raw + 0.08f) {
      smoothedElevation *= 0.55f;
      if (smoothedElevation < 0.06f) {
        smoothedElevation = 0f;
      }
    }
    updateRaisedFromElevation(smoothedElevation);
    return smoothedElevation;
  }

  boolean isRaised() {
    return raised;
  }

  float getSmoothedElevation() {
    return smoothedElevation;
  }

  void reset() {
    smoothedElevation = 0f;
    raised = false;
    lastToggleMs = 0L;
  }

  private static float computeRawElevation(float shoulderY, float handY) {
    float low = shoulderY + 0.12f;
    float high = shoulderY - 0.20f;
    float span = low - high;
    if (span < 0.05f) {
      return 0f;
    }
    float t = (low - handY) / span;
    if (t < 0f) {
      return 0f;
    }
    if (t > 1f) {
      return 1f;
    }
    return t;
  }

  private void updateRaisedFromElevation(float elevation) {
    boolean wantRaised;
    if (raised) {
      wantRaised = elevation >= RAISE_EXIT_ELEV;
    } else {
      wantRaised = elevation >= RAISE_ENTER_ELEV;
    }
    setRaised(wantRaised);
  }

  private void setRaised(boolean wantRaised) {
    long now = System.currentTimeMillis();
    if (!raised && wantRaised && now - lastToggleMs < MIN_TOGGLE_MS) {
      return;
    }
    if (wantRaised != raised) {
      lastToggleMs = now;
      raised = wantRaised;
    }
  }
}
