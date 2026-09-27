package com.felipe.elftemplate.tracking;

import java.util.List;

/**
 * Pipeline depth → blobs → seleção de alvo → anatomia (cabeça, mãos, gestos).
 */
public class DepthFrameProcessor {

  private final DepthHistogramSegmenter segmenter = new DepthHistogramSegmenter();
  private final DepthBlobClusterer blobClusterer = new DepthBlobClusterer();
  private final PersonTargetSelector targetSelector = new PersonTargetSelector();
  private final KinectGestureClassifier gestureClassifier = new KinectGestureClassifier();
  private final TrackingResult result = new TrackingResult();

  public TrackingResult process(
      short[] depthData,
      int width,
      int height,
      TrackingModeConfig modeConfig,
      float baselineCentroidY,
      boolean baselineReady) {

    result.reset();
    if (depthData == null || width <= 0 || height <= 0) {
      return result;
    }

    int peakZ = segmenter.findPeakDepth(segmenter.computeHistogram(depthData, width, height));
    if (peakZ > 0) {
      segmenter.updateProximityLatch(peakZ);
      result.diagnostics.peakDepthZ = peakZ;
      result.diagnostics.isNearProximityMode = segmenter.isProximityMode();
    }

    List<PersonBlob> blobs = blobClusterer.cluster(depthData, width, height);
    result.diagnostics.trackedPersonCount = blobs.size();
    result.diagnostics.trackingMode = modeConfig.getMode().name();

    PersonBlob primary = targetSelector.selectPrimary(blobs, modeConfig);
    if (primary == null) {
      return result;
    }

    DepthBlobAnatomy.fill(depthData, width, height, primary, result, gestureClassifier);
    return result;
  }

  public KinectGestureClassifier getGestureClassifier() {
    return gestureClassifier;
  }

  public void reset() {
    segmenter.reset();
    gestureClassifier.reset();
  }
}
