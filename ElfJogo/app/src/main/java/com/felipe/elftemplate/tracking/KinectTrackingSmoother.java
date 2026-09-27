package com.felipe.elftemplate.tracking;

/** Suavização EMA do esqueleto para overlay/HUD (todos os joints, não só mãos). */
final class KinectTrackingSmoother {

  private static final float EMA_ALPHA = 0.70f;

  private final TrackingResult smoothedResult = new TrackingResult();

  TrackingResult peek() {
    return smoothedResult;
  }

  TrackingResult apply(TrackingResult raw) {
    if (!raw.isPlayerPresent) {
      smoothedResult.isPlayerPresent = false;
      return raw;
    }
    smoothedResult.isPlayerPresent = true;
    smoothedResult.playerCentroidX =
        (EMA_ALPHA * raw.playerCentroidX) + ((1.0f - EMA_ALPHA) * smoothedResult.playerCentroidX);
    smoothedResult.playerCentroidY =
        (EMA_ALPHA * raw.playerCentroidY) + ((1.0f - EMA_ALPHA) * smoothedResult.playerCentroidY);
    smoothedResult.playerDistanceZ =
        (int) ((EMA_ALPHA * raw.playerDistanceZ) + ((1.0f - EMA_ALPHA) * smoothedResult.playerDistanceZ));

    smoothJoint(raw.head, smoothedResult.head);
    smoothJoint(raw.neck, smoothedResult.neck);
    smoothJoint(raw.spine, smoothedResult.spine);
    smoothJoint(raw.leftShoulder, smoothedResult.leftShoulder);
    smoothJoint(raw.rightShoulder, smoothedResult.rightShoulder);
    smoothJoint(raw.leftElbow, smoothedResult.leftElbow);
    smoothJoint(raw.rightElbow, smoothedResult.rightElbow);
    smoothJoint(raw.leftHand, smoothedResult.leftHand);
    smoothJoint(raw.rightHand, smoothedResult.rightHand);
    smoothJoint(raw.leftHip, smoothedResult.leftHip);
    smoothJoint(raw.rightHip, smoothedResult.rightHip);
    smoothJoint(raw.leftFoot, smoothedResult.leftFoot);
    smoothJoint(raw.rightFoot, smoothedResult.rightFoot);

    smoothedResult.leftHandX = smoothedResult.leftHand.x;
    smoothedResult.leftHandY = smoothedResult.leftHand.y;
    smoothedResult.rightHandX = smoothedResult.rightHand.x;
    smoothedResult.rightHandY = smoothedResult.rightHand.y;

    smoothedResult.hasFeetInFrame = raw.hasFeetInFrame;
    smoothedResult.hasLegsInFrame = raw.hasLegsInFrame;

    smoothedResult.leftHandElevation = raw.leftHandElevation;
    smoothedResult.rightHandElevation = raw.rightHandElevation;
    smoothedResult.isLeftHandRaised = raw.isLeftHandRaised;
    smoothedResult.isRightHandRaised = raw.isRightHandRaised;
    smoothedResult.isJumping = raw.isJumping;
    smoothedResult.isDucking = raw.isDucking;
    smoothedResult.activeGesture = raw.activeGesture;
    copyDiagnostics(raw, smoothedResult);

    logElbowDriftIfNeeded(raw, smoothedResult);

    return smoothedResult;
  }

  private static void smoothJoint(Joint raw, Joint smoothed) {
    smoothed.smoothWith(raw, EMA_ALPHA);
  }

  private static void copyDiagnostics(TrackingResult raw, TrackingResult out) {
    out.diagnostics.trackedPersonCount = raw.diagnostics.trackedPersonCount;
    out.diagnostics.trackingMode = raw.diagnostics.trackingMode;
    out.diagnostics.isPoseFusionActive = raw.diagnostics.isPoseFusionActive;
    out.diagnostics.isRemoteAuxActive = raw.diagnostics.isRemoteAuxActive;
    out.diagnostics.isSeatedPose = raw.diagnostics.isSeatedPose;
    out.diagnostics.isNearProximityMode = raw.diagnostics.isNearProximityMode;
    out.diagnostics.leftHandPixelCount = raw.diagnostics.leftHandPixelCount;
    out.diagnostics.rightHandPixelCount = raw.diagnostics.rightHandPixelCount;
    out.diagnostics.processingLatencyMs = raw.diagnostics.processingLatencyMs;
    out.diagnostics.cameraFps = raw.diagnostics.cameraFps;
    out.diagnostics.sliceMinZ = raw.diagnostics.sliceMinZ;
    out.diagnostics.sliceMaxZ = raw.diagnostics.sliceMaxZ;
    out.diagnostics.validPixelCount = raw.diagnostics.validPixelCount;
    out.diagnostics.headPixelCount = raw.diagnostics.headPixelCount;
  }

  private static void logElbowDriftIfNeeded(TrackingResult raw, TrackingResult smoothed) {
    // #region agent log
    float drift =
        Math.abs(smoothed.leftElbow.y - raw.leftElbow.y)
            + Math.abs(smoothed.leftElbow.x - raw.leftElbow.x);
    if (drift > 0.08f) {
      DebugTrace.log(
          "F",
          "KinectTrackingSmoother.apply",
          "elbow_smoothing_active",
          "{\"rawElbowY\":"
              + raw.leftElbow.y
              + ",\"smoothElbowY\":"
              + smoothed.leftElbow.y
              + ",\"drift\":"
              + drift
              + "}");
    }
    // #endregion
  }
}
