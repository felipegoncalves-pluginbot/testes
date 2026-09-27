package com.felipe.elftemplate.debug;

import android.graphics.Bitmap;
import com.felipe.elftemplate.movement.RobotHeadController;
import com.felipe.elftemplate.tracking.TrackingResult;
import com.sanbot.debug.DebugPose;
import com.sanbot.debug.SanbotDebugHub;

/** Adapta TrackingResult deste app para o hub genérico {@link SanbotDebugHub}. */
public final class SanbotDebugBridge {

  private static final DebugPose POSE = new DebugPose();

  private SanbotDebugBridge() {}

  public static void publish(
      TrackingResult r,
      Bitmap silhouette,
      int yawCmd,
      int pitchCmd,
      int leftWing,
      int rightWing) {
    fill(POSE, r, yawCmd, pitchCmd, leftWing, rightWing);
    SanbotDebugHub.get().publish(POSE, silhouette);
  }

  static void fill(
      DebugPose dst,
      TrackingResult r,
      int yawCmd,
      int pitchCmd,
      int leftWing,
      int rightWing) {
    if (dst == null || r == null) {
      return;
    }
    dst.playerPresent = r.isPlayerPresent;
    dst.fused = r.diagnostics.isPoseFusionActive;
    dst.seated = r.diagnostics.isSeatedPose;
    dst.nearMode = r.diagnostics.isNearProximityMode;
    dst.leftRaised = r.isLeftHandRaised;
    dst.rightRaised = r.isRightHandRaised;
    dst.jumping = r.isJumping;
    dst.ducking = r.isDucking;
    dst.centroidX = r.playerCentroidX;
    dst.centroidY = r.playerCentroidY;
    dst.distanceZ = r.playerDistanceZ;
    dst.latencyMs = (int) r.diagnostics.processingLatencyMs;
    dst.fps = r.diagnostics.cameraFps;
    dst.leftArmPx = r.diagnostics.leftHandPixelCount;
    dst.rightArmPx = r.diagnostics.rightHandPixelCount;
    dst.people = r.diagnostics.trackedPersonCount;
    dst.pix = r.diagnostics.validPixelCount;
    dst.aspect = r.diagnostics.bodyAspectHW;
    dst.peakZ = r.diagnostics.peakDepthZ;
    if (r.head != null) {
      dst.headX = r.head.x;
      dst.headY = r.head.y;
    }
    dst.neckX = r.neck.x;
    dst.neckY = r.neck.y;
    dst.spineX = r.spine.x;
    dst.spineY = r.spine.y;
    dst.lShoulderX = r.leftShoulder.x;
    dst.lShoulderY = r.leftShoulder.y;
    dst.lElbowX = r.leftElbow.x;
    dst.lElbowY = r.leftElbow.y;
    dst.lHandX = r.leftHand.x;
    dst.lHandY = r.leftHand.y;
    dst.rShoulderX = r.rightShoulder.x;
    dst.rShoulderY = r.rightShoulder.y;
    dst.rElbowX = r.rightElbow.x;
    dst.rElbowY = r.rightElbow.y;
    dst.rHandX = r.rightHand.x;
    dst.rHandY = r.rightHand.y;
    dst.lHipX = r.leftHip.x;
    dst.lHipY = r.leftHip.y;
    dst.rHipX = r.rightHip.x;
    dst.rHipY = r.rightHip.y;
    dst.lElev = r.leftHandElevation;
    dst.rElev = r.rightHandElevation;
    dst.yawCmd = yawCmd;
    dst.pitchCmd = pitchCmd;
    dst.leftWingCmd = leftWing;
    dst.rightWingCmd = rightWing;
    dst.hwYaw = RobotHeadController.calculateHardwareYaw(yawCmd);
    dst.hwPitch = RobotHeadController.calculateHardwarePitch(pitchCmd);
    dst.gesture = r.activeGesture == null ? "IDLE" : r.activeGesture.name();
    if (r.diagnostics.isRemoteAuxActive) {
      dst.mode = "remote-aux";
    } else if (r.diagnostics.isPoseFusionActive) {
      dst.mode = r.diagnostics.isSeatedPose ? "hybrid-seated" : "hybrid";
    } else if (r.diagnostics.isNearProximityMode) {
      dst.mode = "near";
    } else {
      dst.mode = r.hasFeetInFrame ? "full-body" : "torso";
    }
    dst.timestampMs = System.currentTimeMillis();
  }
}
