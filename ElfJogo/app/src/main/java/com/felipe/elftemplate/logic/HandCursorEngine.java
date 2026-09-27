package com.felipe.elftemplate.logic;

import com.felipe.elftemplate.tracking.Joint;
import com.felipe.elftemplate.tracking.PreviewViewport;
import com.felipe.elftemplate.tracking.TrackingResult;

/**
 * Cursor Kinect-style: mão erguida dominante (depth + Holt, sem EMA de corpo) e clique por
 * empurrão (Z) ou permanência (~0,5 s). Um clique por gesto; cooldown evita spam.
 */
public final class HandCursorEngine {

  public static final int PUSH_ENTER_MM = 90;
  public static final int PUSH_EXIT_MM = 55;
  private static final int DEPTH_ASPECT_W = 4;
  private static final int DEPTH_ASPECT_H = 3;
  private static final float MIN_TRACK_NORM = 0.01f;
  private static final float MAX_TRACK_NORM = 0.99f;
  private static final float DWELL_RADIUS = 0.05f;
  private static final float DWELL_REARM_RADIUS = 0.12f;
  private static final long DWELL_MS = 480L;
  private static final long CLICK_COOLDOWN_MS = 900L;
  private static final long VISIBLE_HOLD_MS = 280L;
  private static final float HAND_SWITCH_ELEV_DELTA = 0.1f;

  private final int[] letterbox = new int[4];

  private float screenX;
  private float screenY;
  private boolean visible;
  private boolean pushing;
  private boolean clickPulse;
  private boolean wasPushing;
  private boolean pushLatched;
  private float dwellAnchorX;
  private float dwellAnchorY;
  private long dwellAnchorMs;
  private boolean dwellPrimed;
  private boolean dwellConsumed;
  private long lastClickMs;
  private long visibleUntilMs;

  public void update(TrackingResult result, int viewWidth, int viewHeight) {
    clickPulse = false;
    pushing = false;
    long nowMs = System.currentTimeMillis();
    if (result == null || !result.isPlayerPresent || viewWidth <= 0 || viewHeight <= 0) {
      applyVisibilityHold(nowMs);
      if (!visible) {
        wasPushing = false;
        pushLatched = false;
        dwellPrimed = false;
        dwellConsumed = false;
      }
      return;
    }
    HandPair active = pickActiveHand(result);
    if (active == null || !isTrackable(active.hand)) {
      applyVisibilityHold(nowMs);
      if (!visible) {
        wasPushing = false;
        pushLatched = false;
        dwellPrimed = false;
      }
      return;
    }
    PreviewViewport.letterboxDest(viewWidth, viewHeight, DEPTH_ASPECT_W, DEPTH_ASPECT_H, letterbox);
    screenX = PreviewViewport.mapX(active.hand.x, letterbox[0], letterbox[2]);
    screenY = PreviewViewport.mapY(active.hand.y, letterbox[1], letterbox[3]);
    visible = true;
    visibleUntilMs = nowMs + VISIBLE_HOLD_MS;

    pushing = updatePushLatch(active.hand, active.shoulder, result.spine);
    if (pushing && !wasPushing && nowMs - lastClickMs >= CLICK_COOLDOWN_MS) {
      clickPulse = true;
      lastClickMs = nowMs;
      dwellPrimed = false;
      dwellConsumed = true;
    }
    wasPushing = pushing;
    if (!clickPulse) {
      clickPulse = updateDwell(active.hand, nowMs);
    }
  }

  public boolean isVisible() {
    return visible;
  }

  public float getScreenX() {
    return screenX;
  }

  public float getScreenY() {
    return screenY;
  }

  public boolean isPushing() {
    return pushing;
  }

  public boolean consumeClickPulse() {
    if (!clickPulse) {
      return false;
    }
    clickPulse = false;
    return true;
  }

  public static boolean isPushGesture(Joint hand, Joint shoulder, Joint spine) {
    if (hand == null) {
      return false;
    }
    int refZ = referenceZ(shoulder, spine);
    if (hand.z <= 0 || refZ <= 0) {
      return false;
    }
    return refZ - hand.z >= PUSH_ENTER_MM;
  }

  static HandPair pickActiveHand(TrackingResult result) {
    boolean leftRaised = result.isLeftHandRaised && isTrackable(result.leftHand);
    boolean rightRaised = result.isRightHandRaised && isTrackable(result.rightHand);
    if (!leftRaised && !rightRaised) {
      return null;
    }
    if (leftRaised && !rightRaised) {
      return new HandPair(result.leftHand, result.leftShoulder);
    }
    if (rightRaised && !leftRaised) {
      return new HandPair(result.rightHand, result.rightShoulder);
    }
    boolean preferRight =
        result.rightHandElevation > result.leftHandElevation + HAND_SWITCH_ELEV_DELTA
            || (Math.abs(result.rightHandElevation - result.leftHandElevation)
                    <= HAND_SWITCH_ELEV_DELTA
                && result.diagnostics.rightHandPixelCount
                    > result.diagnostics.leftHandPixelCount);
    if (preferRight) {
      return new HandPair(result.rightHand, result.rightShoulder);
    }
    return new HandPair(result.leftHand, result.leftShoulder);
  }

  private boolean updatePushLatch(Joint hand, Joint shoulder, Joint spine) {
    int refZ = referenceZ(shoulder, spine);
    if (hand.z <= 0 || refZ <= 0) {
      pushLatched = false;
      return false;
    }
    int delta = refZ - hand.z;
    if (pushLatched) {
      pushLatched = delta >= PUSH_EXIT_MM;
    } else {
      pushLatched = delta >= PUSH_ENTER_MM;
    }
    return pushLatched;
  }

  private boolean updateDwell(Joint hand, long nowMs) {
    if (nowMs - lastClickMs < CLICK_COOLDOWN_MS) {
      return false;
    }
    if (dwellConsumed && !movedBeyond(hand, dwellAnchorX, dwellAnchorY, DWELL_REARM_RADIUS)) {
      return false;
    }
    if (dwellConsumed) {
      dwellConsumed = false;
      dwellPrimed = false;
    }
    float dx = hand.x - dwellAnchorX;
    float dy = hand.y - dwellAnchorY;
    float distSq = dx * dx + dy * dy;
    if (!dwellPrimed || distSq > DWELL_RADIUS * DWELL_RADIUS) {
      dwellAnchorX = hand.x;
      dwellAnchorY = hand.y;
      dwellAnchorMs = nowMs;
      dwellPrimed = true;
      return false;
    }
    if (nowMs - dwellAnchorMs >= DWELL_MS) {
      dwellPrimed = false;
      dwellConsumed = true;
      lastClickMs = nowMs;
      return true;
    }
    return false;
  }

  private void applyVisibilityHold(long nowMs) {
    visible = nowMs < visibleUntilMs;
  }

  private static boolean movedBeyond(Joint hand, float ax, float ay, float radius) {
    float dx = hand.x - ax;
    float dy = hand.y - ay;
    return dx * dx + dy * dy > radius * radius;
  }

  private static int referenceZ(Joint shoulder, Joint spine) {
    if (shoulder != null && shoulder.z > 0) {
      return shoulder.z;
    }
    if (spine != null && spine.z > 0) {
      return spine.z;
    }
    return 0;
  }

  private static boolean isTrackable(Joint joint) {
    if (joint == null) {
      return false;
    }
    return joint.x > MIN_TRACK_NORM
        && joint.x < MAX_TRACK_NORM
        && joint.y > MIN_TRACK_NORM
        && joint.y < MAX_TRACK_NORM;
  }

  static final class HandPair {
    final Joint hand;
    final Joint shoulder;

    HandPair(Joint hand, Joint shoulder) {
      this.hand = hand;
      this.shoulder = shoulder;
    }
  }
}
