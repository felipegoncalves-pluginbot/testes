package com.felipe.elftemplate.tracking;

import android.content.Context;
import android.graphics.Bitmap;

/**
 * Depth-first (Kinect): {@link KinectTrackingEngine} local + aux remoto só para braços/gestos quando
 * fresco.
 */
public final class HybridTrackingProvider implements TrackingProvider {

  private static final int OFFLOAD_RGB_W = 320;
  private static final int OFFLOAD_RGB_H = 240;
  private static final int OFFLOAD_DEPTH_W = 64;
  private static final int OFFLOAD_DEPTH_H = 48;
  private static final long FRAME_MIN_INTERVAL_MS = 66L;

  private final LocalKinectTrackingProvider local;
  private final MirrorTrackingRelay relay;
  private final TrackingResult remoteScratch = new TrackingResult();
  private final TrackingResult deliverScratch = new TrackingResult();

  private FrameCallback outerCallback;
  private volatile long lastRemoteAtMs;
  private long lastFrameSentMs;
  private byte[] rgbCropBuf;
  private byte[] rgbDownscaleBuf;
  private short[] depthDownscaleBuf;
  private int[] cropRectCache;
  private int cropRectSrcW;
  private int cropRectSrcH;
  private short[] lastDepth;
  private int lastDepthW;
  private int lastDepthH;

  public HybridTrackingProvider(LocalKinectTrackingProvider local, MirrorTrackingRelay relay) {
    this.local = local;
    this.relay = relay;
    relay.setRemoteResultListener(this::onRemoteTrack);
  }

  @Override
  public Capability getCapability() {
    return Capability.HYBRID;
  }

  @Override
  public void setRgbPreviewListener(RgbPreviewBridge.Listener listener) {
    if (listener == null) {
      local.setRgbPreviewListener(null);
      return;
    }
    local.setRgbPreviewListener(
        (nv21, width, height) -> {
          maybeOfferFrames(nv21, width, height);
          listener.onRgbPreviewFrame(nv21, width, height);
        });
  }

  @Override
  public void setDepthOverlayTransparent(boolean transparent) {
    local.setDepthOverlayTransparent(transparent);
  }

  @Override
  public void start(Context context, FrameCallback callback, CameraController.StreamProfile profile) {
    outerCallback = callback;
    local.setDepthPreviewListener(this::onDepthPreview);
    local.start(context, this::onLocalFrame, profile);
  }

  @Override
  public void stop() {
    outerCallback = null;
    local.setDepthPreviewListener(null);
    local.stop();
  }

  void bindTestCallback(FrameCallback callback) {
    outerCallback = callback;
  }

  void bindDepthForTest(short[] depthMm, int width, int height) {
    lastDepth = depthMm;
    lastDepthW = width;
    lastDepthH = height;
  }

  void ingestLocalFrameForTest(TrackingResult localResult, android.graphics.Bitmap debugSilhouette) {
    onLocalFrame(localResult, debugSilhouette);
  }

  private void onDepthPreview(short[] depthMm, int width, int height) {
    lastDepth = depthMm;
    lastDepthW = width;
    lastDepthH = height;
  }

  private void maybeOfferFrames(byte[] nv21, int width, int height) {
    if (!relay.isAuxiliaryConnected()) {
      return;
    }
    long now = System.currentTimeMillis();
    if (now - lastFrameSentMs < FRAME_MIN_INTERVAL_MS) {
      return;
    }
    lastFrameSentMs = now;

    byte[] rgbSource = nv21;
    int srcW = width;
    int srcH = height;
    if (!MirrorRgbDepthAlign.isFourThree(width, height)) {
      int[] cropRect = cropRectFor(width, height);
      byte[] cropped = MirrorRgbDepthAlign.cropNv21(nv21, width, height, cropRect, rgbCropBuf);
      if (cropped == null) {
        return;
      }
      rgbCropBuf = cropped;
      srcW = cropRect[2];
      srcH = cropRect[3];
      rgbSource = cropped;
    }

    byte[] smallRgb =
        MirrorFrameDownscaler.downscaleNv21(
            rgbSource, srcW, srcH, OFFLOAD_RGB_W, OFFLOAD_RGB_H, rgbDownscaleBuf);
    rgbDownscaleBuf = smallRgb;
    relay.offerRgbFrame(smallRgb, OFFLOAD_RGB_W, OFFLOAD_RGB_H);
    if (lastDepth != null && lastDepthW > 0 && lastDepthH > 0) {
      short[] smallDepth =
          MirrorFrameDownscaler.downscaleDepth(
              lastDepth,
              lastDepthW,
              lastDepthH,
              OFFLOAD_DEPTH_W,
              OFFLOAD_DEPTH_H,
              depthDownscaleBuf);
      depthDownscaleBuf = smallDepth;
      relay.offerDepthFrame(smallDepth, OFFLOAD_DEPTH_W, OFFLOAD_DEPTH_H);
    }
  }

  private void onRemoteTrack(long seq, TrackingResult result, long remoteTimestampMs) {
    lastRemoteAtMs = System.currentTimeMillis();
    remoteScratch.applyFrom(result);
  }

  private void onLocalFrame(TrackingResult localResult, Bitmap debugSilhouette) {
    FrameCallback callback = outerCallback;
    if (callback == null) {
      return;
    }
    callback.onTrackingFrame(selectResult(localResult), debugSilhouette);
  }

  private int[] cropRectFor(int width, int height) {
    if (cropRectCache == null || width != cropRectSrcW || height != cropRectSrcH) {
      cropRectCache = MirrorRgbDepthAlign.cropFourThreeRect(width, height);
      cropRectSrcW = width;
      cropRectSrcH = height;
    }
    return cropRectCache;
  }

  private TrackingResult selectResult(TrackingResult localResult) {
    if (!HybridRemoteMerge.isRemoteUsable(relay, lastRemoteAtMs, remoteScratch)) {
      return localResult;
    }
    if (lastDepth == null || lastDepthW <= 0 || lastDepthH <= 0) {
      return localResult;
    }
    if (HybridRemoteMerge.applyRemoteExtension(
        deliverScratch, localResult, remoteScratch, lastDepth, lastDepthW, lastDepthH)) {
      return deliverScratch;
    }
    return localResult;
  }
}
