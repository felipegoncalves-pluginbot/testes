package com.felipe.elftemplate.tracking;

/**
 * Estilo Kinect NUI: corpo/cabeça vêm do depth local; aux só refina braços e gestos (XY remoto + Z
 * Astra full-res).
 */
final class HybridRemoteArms {

  private static final float ARM_SPATIAL_GATE = 0.38f;

  private HybridRemoteArms() {}

  static boolean applyArmsExtension(
      TrackingResult out,
      TrackingResult local,
      TrackingResult remote,
      short[] depthMm,
      int depthW,
      int depthH) {
    if (out == null || local == null || remote == null) {
      return false;
    }
    if (!local.isPlayerPresent || !remote.isPlayerPresent) {
      return false;
    }
    if (depthMm == null || depthW <= 0 || depthH <= 0) {
      return false;
    }

    out.applyFrom(local);
    mergeArm(out, remote, true, depthMm, depthW, depthH);
    mergeArm(out, remote, false, depthMm, depthW, depthH);

    out.leftHandElevation = remote.leftHandElevation;
    out.rightHandElevation = remote.rightHandElevation;
    out.isLeftHandRaised = remote.isLeftHandRaised;
    out.isRightHandRaised = remote.isRightHandRaised;
    if (remote.activeGesture != KinectTrackingEngine.GestureType.IDLE) {
      out.activeGesture = remote.activeGesture;
    }
    out.leftHandX = out.leftHand.x;
    out.leftHandY = out.leftHand.y;
    out.rightHandX = out.rightHand.x;
    out.rightHandY = out.rightHand.y;
    return true;
  }

  private static void mergeArm(
      TrackingResult out,
      TrackingResult remote,
      boolean left,
      short[] depthMm,
      int depthW,
      int depthH) {
    Joint remoteElbow = left ? remote.leftElbow : remote.rightElbow;
    Joint remoteHand = left ? remote.leftHand : remote.rightHand;
    Joint anchor = left ? out.leftShoulder : out.rightShoulder;
    if (!hasRemoteJoint(remoteHand) || anchor == null) {
      return;
    }
    if (Math.abs(remoteHand.x - anchor.x) > ARM_SPATIAL_GATE) {
      return;
    }
    Joint outElbow = left ? out.leftElbow : out.rightElbow;
    Joint outHand = left ? out.leftHand : out.rightHand;
    if (hasRemoteJoint(remoteElbow)) {
      copyJointWithDepth(outElbow, remoteElbow, depthMm, depthW, depthH);
    }
    copyJointWithDepth(outHand, remoteHand, depthMm, depthW, depthH);
  }

  private static boolean hasRemoteJoint(Joint joint) {
    if (joint == null) {
      return false;
    }
    return joint.x > 0.02f && joint.x < 0.98f && joint.y > 0.02f && joint.y < 0.98f;
  }

  private static void copyJointWithDepth(
      Joint dest, Joint src, short[] depthMm, int depthW, int depthH) {
    if (dest == null || src == null) {
      return;
    }
    int z = PoseDepthFusion.sampleDepthAtNorm(depthMm, depthW, depthH, src.x, src.y);
    if (z > 0) {
      dest.set(src.x, src.y, z);
    } else {
      dest.set(src.x, src.y, dest.z > 0 ? dest.z : src.z);
    }
  }
}
