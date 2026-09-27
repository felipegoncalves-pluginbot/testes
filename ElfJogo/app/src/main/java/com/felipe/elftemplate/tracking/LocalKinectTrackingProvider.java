package com.felipe.elftemplate.tracking;

import android.content.Context;
import com.sanbot.opensdk.function.unit.HDCameraManager;

/** Adapter local: encapsula {@link KinectTrackingEngine} como {@link TrackingProvider}. */
public final class LocalKinectTrackingProvider implements TrackingProvider {

  private final KinectTrackingEngine engine = new KinectTrackingEngine();
  private HDCameraManager hdCamera;

  public void setHdCamera(HDCameraManager hdCamera) {
    this.hdCamera = hdCamera;
  }

  public void setLowLatencyHandListener(KinectTrackingEngine.LowLatencyHandListener listener) {
    engine.setLowLatencyHandListener(listener);
  }

  public void setDepthPreviewListener(DepthPreviewBridge.Listener listener) {
    engine.setDepthPreviewListener(listener);
  }

  @Override
  public Capability getCapability() {
    return Capability.LOCAL_KINECT;
  }

  @Override
  public void setRgbPreviewListener(RgbPreviewBridge.Listener listener) {
    engine.setRgbPreviewListener(listener);
  }

  @Override
  public void setDepthOverlayTransparent(boolean transparent) {
    engine.setDepthOverlayTransparent(transparent);
  }

  @Override
  public void start(
      Context context,
      final FrameCallback callback,
      CameraController.StreamProfile cameraProfile) {
    engine.start(
        context,
        new KinectTrackingEngine.TrackingCallback() {
          @Override
          public void onTrackingUpdate(
              TrackingResult result, android.graphics.Bitmap debugSilhouette) {
            if (callback != null) {
              callback.onTrackingFrame(result, debugSilhouette);
            }
          }
        },
        hdCamera,
        cameraProfile);
  }

  @Override
  public void stop() {
    engine.stop();
  }
}
