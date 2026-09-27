package com.felipe.elfmirror.tracking;

/** Elevação de braço 0..1 com EMA + Schmitt — espelho do host. */
final class ArmElevationTracker {

  private static final float EMA_ALPHA = 0.38f;
  private static final float RAISE_ENTER_ELEV = 0.35f;
  private static final float RAISE_EXIT_ELEV = 0.20f;
  private static final long MIN_TOGGLE_MS = 200L;
  private static final float FORWARD_REACH_SCALE = 0.72f;
  private static final float FORWARD_REACH_CAP = 0.62f;
  private static final float DECAY_FACTOR = 0.82f;

  private float smoothedElevation;
  private boolean raised;
  private long lastToggleMs;

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
    updateRaisedFromElevation(smoothedElevation);
    return smoothedElevation;
  }

  boolean isRaised() {
    return raised;
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
