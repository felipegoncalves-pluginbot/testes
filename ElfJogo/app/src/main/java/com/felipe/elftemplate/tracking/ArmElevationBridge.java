package com.felipe.elftemplate.tracking;

/** Recalcula elevação/levantada a partir de ombro↔punho detectados (sem suposições fixas). */
final class ArmElevationBridge {

  private static final float LATERAL_SEPARATION_MIN = 0.07f;
  /** Mão no queixo/rosto: perto da cabeça e não acima dela (não é HANDS_UP). */
  static final float FACE_REST_RADIUS = 0.18f;
  static final float FACE_REST_ABOVE_HEAD = 0.06f;

  private ArmElevationBridge() {}

  static boolean isRestingOnHead(Joint hand, Joint head) {
    if (hand == null || head == null) {
      return false;
    }
    float dx = hand.x - head.x;
    float dy = hand.y - head.y;
    if (dx * dx + dy * dy > FACE_REST_RADIUS * FACE_REST_RADIUS) {
      return false;
    }
    return hand.y >= head.y - FACE_REST_ABOVE_HEAD;
  }

  static void syncFromJoints(TrackingResult result, KinectGestureClassifier classifier) {
    if (!result.isPlayerPresent) {
      return;
    }
    float shoulderY = (result.leftShoulder.y + result.rightShoulder.y) * 0.5f;
    boolean leftSep =
        Math.abs(result.leftHand.x - result.leftShoulder.x) >= LATERAL_SEPARATION_MIN;
    boolean rightSep =
        Math.abs(result.rightHand.x - result.rightShoulder.x) >= LATERAL_SEPARATION_MIN;
    boolean restPose =
        result.diagnostics.isNearProximityMode || result.diagnostics.isSeatedPose;
    float leftHandY = result.leftHand.y;
    float rightHandY = result.rightHand.y;
    if (restPose && isRestingOnHead(result.leftHand, result.head)) {
      leftHandY = shoulderY + 0.15f;
      leftSep = false;
    }
    if (restPose && isRestingOnHead(result.rightHand, result.head)) {
      rightHandY = shoulderY + 0.15f;
      rightSep = false;
    }

    classifier.updateLeftArmState(leftHandY, shoulderY, leftSep);
    classifier.updateRightArmState(rightHandY, shoulderY, rightSep);

    result.leftHandElevation = classifier.getLeftArmElevation();
    result.rightHandElevation = classifier.getRightArmElevation();
    result.isLeftHandRaised = classifier.isLeftArmRaised();
    result.isRightHandRaised = classifier.isRightArmRaised();
  }
}
