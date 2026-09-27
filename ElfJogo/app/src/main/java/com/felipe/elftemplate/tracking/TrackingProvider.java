package com.felipe.elftemplate.tracking;

import android.content.Context;
import android.graphics.Bitmap;

/**
 * Porta de rastreamento corporal plugável. Implementações locais (Kinect) ou remotas (WiFi) entregam
 * {@link TrackingResult} no mesmo contrato.
 */
public interface TrackingProvider {

  enum Capability {
    LOCAL_KINECT,
    REMOTE,
    HYBRID
  }

  interface FrameCallback {
    void onTrackingFrame(TrackingResult result, Bitmap debugSilhouette);
  }

  Capability getCapability();

  void setRgbPreviewListener(RgbPreviewBridge.Listener listener);

  void setDepthOverlayTransparent(boolean transparent);

  void start(
      android.content.Context context,
      FrameCallback callback,
      CameraController.StreamProfile cameraProfile);

  void stop();
}
