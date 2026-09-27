package com.felipe.elftemplate.mirror;

import android.content.Context;
import android.graphics.Bitmap;
import com.felipe.elftemplate.debug.SanbotDebugBridge;
import com.felipe.elftemplate.logic.MirrorGameEngine;
import com.felipe.elftemplate.movement.RobotGameFeedback;
import com.felipe.elftemplate.tracking.ArmElevationMapper;
import com.felipe.elftemplate.tracking.CameraController;
import com.felipe.elftemplate.tracking.KinectTrackingEngine;
import com.felipe.elftemplate.tracking.PreviewViewport;
import com.felipe.elftemplate.tracking.RgbPreviewBridge;
import com.felipe.elftemplate.tracking.TrackingProvider;
import com.felipe.elftemplate.tracking.TrackingResult;

/**
 * Orquestra modo espelho: tracking (porta) → cinemática → motores. A Activity só cuida de UI e
 * lifecycle.
 */
public class MirrorSessionController {

  public interface OverlayListener {
    void onMirrorOverlayUpdate(TrackingResult result, Bitmap silhouette, float rgbPanNorm);
  }

  private final TrackingProvider trackingProvider;
  private final MirrorGameEngine mirrorEngine = new MirrorGameEngine();
  private RobotGameFeedback robotFeedback;
  private OverlayListener overlayListener;
  private volatile boolean stopped;

  public MirrorSessionController(TrackingProvider trackingProvider) {
    this.trackingProvider = trackingProvider;
  }

  public TrackingProvider.Capability getTrackingCapability() {
    return trackingProvider.getCapability();
  }

  public void setRobotFeedback(RobotGameFeedback feedback) {
    this.robotFeedback = feedback;
  }

  public void setOverlayListener(OverlayListener listener) {
    this.overlayListener = listener;
  }

  public void setRgbPreviewListener(RgbPreviewBridge.Listener listener) {
    trackingProvider.setRgbPreviewListener(listener);
  }

  public void prepareFullscreenSession() {
    trackingProvider.setDepthOverlayTransparent(true);
  }

  public void start(Context context, CameraController.StreamProfile cameraProfile) {
    stopped = false;
    trackingProvider.start(context, this::onTrackingFrame, cameraProfile);
  }

  public void stop() {
    stopped = true;
    trackingProvider.stop();
    if (robotFeedback != null) {
      robotFeedback.stopAll();
    }
  }

  private void onTrackingFrame(TrackingResult result, Bitmap debugSilhouette) {
    if (stopped) {
      return;
    }
    float rgbPanNorm = applyMirrorMotors(result, debugSilhouette);
    OverlayListener listener = overlayListener;
    if (listener != null) {
      listener.onMirrorOverlayUpdate(result, debugSilhouette, rgbPanNorm);
    }
  }

  private float applyMirrorMotors(TrackingResult result, Bitmap debugSilhouette) {
    mirrorEngine.processTracking(result);
    float rgbPanNorm = PreviewViewport.panNormFromHeadYaw(mirrorEngine.getTargetHeadYaw());
    if (!result.isPlayerPresent) {
      rgbPanNorm = 0f;
      SanbotDebugBridge.publish(result, debugSilhouette, 0, 0, 0, 0);
      if (robotFeedback != null) {
        robotFeedback.setMirrorWingAngles(
            ArmElevationMapper.WING_ANGLE_MIN, ArmElevationMapper.WING_ANGLE_MIN);
        robotFeedback.setMirrorHead2D(0, 0);
      }
      return rgbPanNorm;
    }
    int leftAngle = mirrorEngine.getTargetLeftWingAngle();
    int rightAngle = mirrorEngine.getTargetRightWingAngle();
    int headYaw = mirrorEngine.getTargetHeadYaw();
    int headPitch = mirrorEngine.getTargetHeadPitch();
    SanbotDebugBridge.publish(result, debugSilhouette, headYaw, headPitch, leftAngle, rightAngle);
    if (robotFeedback == null) {
      return rgbPanNorm;
    }
    robotFeedback.setMirrorWingAngles(leftAngle, rightAngle);
    if (result.activeGesture == KinectTrackingEngine.GestureType.HEAD_NOD_YES) {
      robotFeedback.performHeadNodYes();
    } else if (result.activeGesture == KinectTrackingEngine.GestureType.HEAD_SHAKE_NO) {
      robotFeedback.performHeadShakeNo();
    } else {
      robotFeedback.setMirrorHead2D(headYaw, headPitch);
    }
    return rgbPanNorm;
  }
}
