package com.felipe.elftemplate.tracking;

/**
 * Fusão de cotovelos e punhos MoveNet TFLite com profundidade Astra (ver {@link PoseDepthFusion#POSE_MAX_AGE_MS}).
 */
final class PoseDepthFusionArms {

  private static final float FOREARM_EXTENSION_RATIO = 0.9f;

  private PoseDepthFusionArms() {}

  static void fuseLimbs(
      TrackingResult result,
      PoseFrame poseFrame,
      short[] depthData,
      int dW,
      int dH) {

    // Braço esquerdo no frame da câmera (screen-left) corresponde ao lado direito anatômico do usuário (RIGHT_*)
    fuseArm(
        result,
        true,
        poseFrame.getLandmark(PoseFrame.RIGHT_WRIST),
        poseFrame.getLandmark(PoseFrame.RIGHT_ELBOW),
        depthData,
        dW,
        dH);
    // Braço direito no frame da câmera (screen-right) corresponde ao lado esquerdo anatômico do usuário (LEFT_*)
    fuseArm(
        result,
        false,
        poseFrame.getLandmark(PoseFrame.LEFT_WRIST),
        poseFrame.getLandmark(PoseFrame.LEFT_ELBOW),
        depthData,
        dW,
        dH);
  }

  private static void fuseArm(
      TrackingResult result,
      boolean left,
      PoseLandmarkData wristLm,
      PoseLandmarkData elbowLm,
      short[] depthData,
      int dW,
      int dH) {

    Joint shoulder = left ? result.leftShoulder : result.rightShoulder;
    if (shoulder == null) {
      return;
    }

    if (PoseDepthFusion.isConfident(elbowLm)) {
      PoseDepthFusion.writeJoint(left ? result.leftElbow : result.rightElbow, elbowLm, depthData, dW, dH);
    }

    if (PoseDepthFusion.isConfident(wristLm)) {
      Joint hand = left ? result.leftHand : result.rightHand;
      PoseDepthFusion.writeJoint(hand, wristLm, depthData, dW, dH);
      assignHand(result, left, hand);
    }
    // Punho MoveNet perdido: mantém a mão do depth-blob. Kinect SDK marca NotTracked e
    // não inventa o punho (JointSmoothing White Paper). Extrapolação clampava X em 0.

    if (!PoseDepthFusion.isConfident(elbowLm)) {
      reconcileElbowMidpoint(result, left, shoulder);
    }
  }

  private static void reconcileElbowMidpoint(
      TrackingResult result, boolean left, Joint shoulder) {
    Joint hand = left ? result.leftHand : result.rightHand;
    if (hand == null) {
      return;
    }
    Joint elbow = left ? result.leftElbow : result.rightElbow;
    elbow.set(
        (shoulder.x + hand.x) * 0.5f,
        (shoulder.y + hand.y) * 0.5f,
        (shoulder.z + hand.z) / 2);
  }

  static void reconcileElbowMidpointForTest(TrackingResult result, boolean left) {
    Joint shoulder = left ? result.leftShoulder : result.rightShoulder;
    if (shoulder == null) {
      return;
    }
    reconcileElbowMidpoint(result, left, shoulder);
  }

  static void extrapolateHandFromElbow(
      Joint dest, Joint shoulder, Joint elbow, short[] depthData, int dW, int dH) {
    float dx = elbow.x - shoulder.x;
    float dy = elbow.y - shoulder.y;
    float hx = elbow.x + dx * FOREARM_EXTENSION_RATIO;
    float hy = elbow.y + dy * FOREARM_EXTENSION_RATIO;
    hx = Math.max(0.0f, Math.min(1.0f, hx));
    hy = Math.max(0.0f, Math.min(1.0f, hy));
    int z = PoseDepthFusion.sampleDepthAtNorm(depthData, dW, dH, hx, hy);
    if (z <= 0) {
      z = (shoulder.z + elbow.z) / 2;
    }
    dest.set(hx, hy, z);
  }

  private static void assignHand(TrackingResult result, boolean left, Joint hand) {
    if (left) {
      result.leftHandX = hand.x;
      result.leftHandY = hand.y;
    } else {
      result.rightHandX = hand.x;
      result.rightHandY = hand.y;
    }
  }
}
