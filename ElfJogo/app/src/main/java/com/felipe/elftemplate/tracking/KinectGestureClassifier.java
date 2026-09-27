package com.felipe.elftemplate.tracking;

/** Classificador de gestos, posturas corporais e histerese Schmitt-Trigger para o Sanbot Elf. */
public class KinectGestureClassifier {

  private static final int SWING_COOLDOWN_MS = 250;

  private final ArmElevationTracker leftArmTracker = new ArmElevationTracker();
  private final ArmElevationTracker rightArmTracker = new ArmElevationTracker();
  private boolean isJumpingPrev = false;
  private boolean isDuckingPrev = false;

  private static final float JUMP_ENTER_DELTA = -0.09f;
  private static final float JUMP_EXIT_DELTA = -0.05f;
  private static final float DUCK_ENTER_DELTA = 0.12f;
  private static final float DUCK_EXIT_DELTA = 0.08f;

  private float lastLeftHandTrackX = 0.2f;
  private float lastLeftHandTrackY = 0.6f;
  private long lastLeftHandTime = 0;

  private float lastRightHandTrackX = 0.8f;
  private float lastRightHandTrackY = 0.6f;
  private long lastRightHandTime = 0;
  private long lastGestureTriggerTime = 0;

  public boolean updateLeftArmState(float handY, float shoulderY, boolean isSeparated) {
    leftArmTracker.update(shoulderY, handY, true, isSeparated);
    return leftArmTracker.isRaised();
  }

  public boolean updateRightArmState(float handY, float shoulderY, boolean isSeparated) {
    rightArmTracker.update(shoulderY, handY, true, isSeparated);
    return rightArmTracker.isRaised();
  }

  public void markLeftArmMissing() {
    leftArmTracker.update(0.5f, 0.8f, false, false);
  }

  public void markRightArmMissing() {
    rightArmTracker.update(0.5f, 0.8f, false, false);
  }

  public boolean isLeftArmRaised() {
    return leftArmTracker.isRaised();
  }

  public boolean isRightArmRaised() {
    return rightArmTracker.isRaised();
  }

  public float getLeftArmElevation() {
    return leftArmTracker.getSmoothedElevation();
  }

  public float getRightArmElevation() {
    return rightArmTracker.getSmoothedElevation();
  }

  public void applyHandRaiseHysteresis(TrackingResult result) {
    float shoulderY = (result.leftShoulder.y + result.rightShoulder.y) * 0.5f;
    leftArmTracker.update(shoulderY, result.leftHand.y, true, true);
    rightArmTracker.update(shoulderY, result.rightHand.y, true, true);
    result.leftHandElevation = leftArmTracker.getSmoothedElevation();
    result.rightHandElevation = rightArmTracker.getSmoothedElevation();
    result.isLeftHandRaised = leftArmTracker.isRaised();
    result.isRightHandRaised = rightArmTracker.isRaised();
  }

  public void classifyPostures(TrackingResult result, float deltaFromBaseline) {
    updateJumpDuckHysteresis(result, deltaFromBaseline);

    if (result.isJumping) {
      result.activeGesture = KinectTrackingEngine.GestureType.JUMP;
    } else if (result.isDucking) {
      result.activeGesture = KinectTrackingEngine.GestureType.DUCK;
    } else if (isTPoseGeometry(result)) {
      result.activeGesture = KinectTrackingEngine.GestureType.T_POSE;
    } else if (result.isLeftHandRaised && result.isRightHandRaised) {
      result.activeGesture = KinectTrackingEngine.GestureType.HANDS_UP;
    } else if (result.isRightHandRaised) {
      result.activeGesture = KinectTrackingEngine.GestureType.RIGHT_HAND_UP;
    } else if (result.isLeftHandRaised) {
      result.activeGesture = KinectTrackingEngine.GestureType.LEFT_HAND_UP;
    } else {
      result.activeGesture = KinectTrackingEngine.GestureType.IDLE;
    }
  }

  private void updateJumpDuckHysteresis(TrackingResult result, float baselineDeltaY) {
    if (result.diagnostics.isNearProximityMode) {
      isJumpingPrev = false;
      isDuckingPrev = false;
      result.isJumping = false;
      result.isDucking = false;
      return;
    }

    if (isJumpingPrev) {
      isJumpingPrev = baselineDeltaY < JUMP_EXIT_DELTA;
    } else {
      isJumpingPrev = baselineDeltaY < JUMP_ENTER_DELTA;
    }

    if (isDuckingPrev) {
      isDuckingPrev = baselineDeltaY > DUCK_EXIT_DELTA;
    } else {
      isDuckingPrev = baselineDeltaY > DUCK_ENTER_DELTA;
    }

    result.isJumping = isJumpingPrev;
    result.isDucking = isDuckingPrev;
  }

  public KinectTrackingEngine.GestureType updateHandTrajectories(
      float lx,
      float ly,
      boolean leftRaised,
      float rx,
      float ry,
      boolean rightRaised,
      long timestamp) {

    if (timestamp - lastGestureTriggerTime < SWING_COOLDOWN_MS) {
      return KinectTrackingEngine.GestureType.IDLE;
    }

    KinectTrackingEngine.GestureType gesture = KinectTrackingEngine.GestureType.IDLE;
    if (lastRightHandTime > 0) {
      float dt = (timestamp - lastRightHandTime) / 1000.0f;
      if (dt > 0.015f && dt < 0.25f) {
        float vx = (rx - lastRightHandTrackX) / dt;
        float vy = (ry - lastRightHandTrackY) / dt;
        if (vx < -2.2f && Math.abs(vy) < 1.8f) {
          gesture = KinectTrackingEngine.GestureType.SWIPE_LEFT;
        } else if (vy < -2.0f && Math.abs(vx) < 1.8f) {
          gesture = KinectTrackingEngine.GestureType.SWING_UP;
        }
      }
    }
    lastRightHandTrackX = rx;
    lastRightHandTrackY = ry;
    lastRightHandTime = timestamp;

    if (gesture == KinectTrackingEngine.GestureType.IDLE && lastLeftHandTime > 0) {
      float dt = (timestamp - lastLeftHandTime) / 1000.0f;
      if (dt > 0.015f && dt < 0.25f) {
        float vx = (lx - lastLeftHandTrackX) / dt;
        float vy = (ly - lastLeftHandTrackY) / dt;
        if (vx > 2.2f && Math.abs(vy) < 1.8f) {
          gesture = KinectTrackingEngine.GestureType.SWIPE_RIGHT;
        } else if (vy < -2.0f && Math.abs(vx) < 1.8f) {
          gesture = KinectTrackingEngine.GestureType.SWING_UP;
        }
      }
    }
    lastLeftHandTrackX = lx;
    lastLeftHandTrackY = ly;
    lastLeftHandTime = timestamp;

    if (gesture != KinectTrackingEngine.GestureType.IDLE) {
      lastGestureTriggerTime = timestamp;
    }
    return gesture;
  }

  private static boolean isTPoseGeometry(TrackingResult result) {
    boolean armsHorizontal =
        Math.abs(result.leftHandY - result.leftShoulder.y) < 0.12f
            && Math.abs(result.rightHandY - result.rightShoulder.y) < 0.12f;
    return armsHorizontal && (result.rightHandX - result.leftHandX > 0.45f);
  }

  public void reset() {
    leftArmTracker.reset();
    rightArmTracker.reset();
    isJumpingPrev = false;
    isDuckingPrev = false;
    lastGestureTriggerTime = 0;
    lastLeftHandTime = 0;
    lastRightHandTime = 0;
  }
}
