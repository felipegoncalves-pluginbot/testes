package com.felipe.elftemplate.tracking;

/**
 * Fusão host↔aux estilo Kinect: depth local é a fonte do esqueleto; remoto só quando conectado,
 * fresco e {@code present}, refinando braços/gestos.
 */
final class HybridRemoteMerge {

  private HybridRemoteMerge() {}

  static boolean isRemoteUsable(
      MirrorTrackingRelay relay, long lastRemoteAtMs, TrackingResult remote) {
    if (relay == null || remote == null || !relay.isAuxiliaryConnected()) {
      return false;
    }
    if (lastRemoteAtMs <= 0 || !remote.isPlayerPresent) {
      return false;
    }
    long age = System.currentTimeMillis() - lastRemoteAtMs;
    return age <= MirrorTrackingRelay.REMOTE_RESULT_MAX_AGE_MS;
  }

  static boolean applyRemoteExtension(
      TrackingResult out,
      TrackingResult local,
      TrackingResult remote,
      short[] depthMm,
      int depthW,
      int depthH) {
    if (!HybridRemoteArms.applyArmsExtension(out, local, remote, depthMm, depthW, depthH)) {
      return false;
    }
    preserveLocalDiagnostics(out.diagnostics, local.diagnostics);
    out.diagnostics.isRemoteAuxActive = true;
    return true;
  }

  private static void preserveLocalDiagnostics(
      TrackingDiagnostics dst, TrackingDiagnostics local) {
    if (dst == null || local == null) {
      return;
    }
    dst.cameraFps = local.cameraFps;
    dst.frameWidth = local.frameWidth;
    dst.frameHeight = local.frameHeight;
    dst.processingLatencyMs = local.processingLatencyMs;
    dst.trackedPersonCount = local.trackedPersonCount;
    dst.trackingMode = local.trackingMode;
    dst.isNearProximityMode = local.isNearProximityMode;
    dst.isSeatedPose = local.isSeatedPose;
    dst.isPoseFusionActive = local.isPoseFusionActive;
    dst.peakDepthZ = local.peakDepthZ;
    dst.validPixelCount = local.validPixelCount;
  }
}
