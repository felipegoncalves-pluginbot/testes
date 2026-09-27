package com.felipe.elftemplate.tracking;

/** Quando ML Kit e depth discordam no eixo Y, limita salto (Kinect fMaxDeviationRadius). */
final class PoseDepthHandBlend {

  private static final float MAX_BLEND_DEVIATION = 0.10f;
  private static final float DEPTH_WEIGHT_ON_CONFLICT = 0.55f;

  private PoseDepthHandBlend() {}

  static void blendVerticalWithDepth(
      TrackingResult fused, float depthLeftHandY, float depthRightHandY) {
    fused.leftHand.y = blendY(fused.leftHand.y, depthLeftHandY);
    fused.rightHand.y = blendY(fused.rightHand.y, depthRightHandY);
    fused.leftHandY = fused.leftHand.y;
    fused.rightHandY = fused.rightHand.y;
  }

  private static float blendY(float poseY, float depthY) {
    // Logcat no Elf: depth blob marca pé/quadril (y≈0.79–0.90) como mão.
    // Se o depth está abaixo do quadril, o RGB/MoveNet é a fonte (model card MoveNet).
    if (depthY > 0.68f && poseY < depthY - 0.06f) {
      return poseY;
    }
    float diff = poseY - depthY;
    if (Math.abs(diff) <= MAX_BLEND_DEVIATION) {
      return poseY;
    }
    float clampedPose = depthY + (diff > 0 ? MAX_BLEND_DEVIATION : -MAX_BLEND_DEVIATION);
    return clampedPose * (1f - DEPTH_WEIGHT_ON_CONFLICT) + depthY * DEPTH_WEIGHT_ON_CONFLICT;
  }
}
