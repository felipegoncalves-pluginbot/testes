package com.felipe.elftemplate.tracking;

import android.content.Context;
import android.graphics.Rect;
import android.util.Log;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Rastreador Híbrido MoveNet TFLite + Astra Profundidade 3D para o Siga-me (FollowActivity).
 */
public class HybridPersonTracker {

  private static final String TAG = "HybridTracker";
  private static final float ASTRA_MAX_DIST_M = 4.0f;
  private static final float ASTRA_MIN_DIST_M = 0.5f;
  private static final float POSE_CONFIDENCE_THRESHOLD = 0.35f;

  public interface HybridTrackingListener {
    void onPersonDetected(float normalizedX, float distanceMeters, Rect boundingBox);

    void onPersonLost();
  }

  private final HybridTrackingListener listener;
  private final TFLitePoseProcessor tflitePoseProcessor = new TFLitePoseProcessor();

  private short[] latestDepthData;
  private int depthWidth = 640;
  private int depthHeight = 480;

  private final AtomicBoolean isProcessing = new AtomicBoolean(false);
  private long lastDetectionTime = 0;
  private static final long LOST_TIMEOUT_MS = 1500;

  public HybridPersonTracker(Context context, HybridTrackingListener listener) {
    this.listener = listener;
    if (context != null) {
      tflitePoseProcessor.initInterpreter(context);
    }
  }

  public void updateDepthFrame(short[] depthData, int width, int height) {
    this.latestDepthData = depthData;
    this.depthWidth = width;
    this.depthHeight = height;
  }

  public void processRgbFrame(byte[] yuvData, int width, int height) {
    try {
      tflitePoseProcessor.processRgb(yuvData, width, height);
      PoseFrame frame = tflitePoseProcessor.getLatestPoseFrame();
      processPose(frame, width, height);
    } catch (Throwable t) {
      Log.e(TAG, "Erro no processamento MoveNet: " + t.getMessage(), t);
    } finally {
      isProcessing.set(false);
    }
  }

  public boolean isProcessing() {
    return isProcessing.get();
  }

  public void setProcessing(boolean processing) {
    isProcessing.set(processing);
  }

  private void processPose(PoseFrame frame, int rgbWidth, int rgbHeight) {
    if (frame == null || frame.landmarks == null) {
      checkLostTimeout();
      return;
    }

    PoseLandmarkData leftS = frame.getLandmark(PoseFrame.LEFT_SHOULDER);
    PoseLandmarkData rightS = frame.getLandmark(PoseFrame.RIGHT_SHOULDER);
    PoseLandmarkData leftH = frame.getLandmark(PoseFrame.LEFT_HIP);
    PoseLandmarkData rightH = frame.getLandmark(PoseFrame.RIGHT_HIP);
    PoseLandmarkData nose = frame.getLandmark(PoseFrame.NOSE);

    PoseLandmarkData bestLeft = null;
    PoseLandmarkData bestRight = null;

    if (isConfident(leftS) && isConfident(rightS)) {
      bestLeft = leftS;
      bestRight = rightS;
    } else if (isConfident(leftH) && isConfident(rightH)) {
      bestLeft = leftH;
      bestRight = rightH;
    }

    float normalizedX = -1f;
    float bodyWidth = 0.25f;

    if (bestLeft != null && bestRight != null) {
      normalizedX = (bestLeft.x + bestRight.x) * 0.5f;
      bodyWidth = Math.abs(bestLeft.x - bestRight.x) * 1.5f;
    } else if (isConfident(nose)) {
      normalizedX = nose.x;
    }

    if (normalizedX >= 0f) {
      float centerX_px = normalizedX * rgbWidth;
      float bodyWidth_px = bodyWidth * rgbWidth;
      float left = Math.max(0, centerX_px - bodyWidth_px / 2f);
      float right = Math.min(rgbWidth, centerX_px + bodyWidth_px / 2f);
      Rect bounds =
          new Rect((int) left, (int) (rgbHeight * 0.2f), (int) right, (int) (rgbHeight * 0.8f));

      float distanceMeters = estimateDistanceAt(normalizedX);
      if (distanceMeters > 0) {
        lastDetectionTime = System.currentTimeMillis();
        if (listener != null) {
          listener.onPersonDetected(normalizedX, distanceMeters, bounds);
        }
      } else {
        checkLostTimeout();
      }
    } else {
      checkLostTimeout();
    }
  }

  private static boolean isConfident(PoseLandmarkData lm) {
    return lm != null && lm.score >= POSE_CONFIDENCE_THRESHOLD;
  }

  private float estimateDistanceAt(float normalizedX) {
    short[] depth = latestDepthData;
    if (depth == null) return -1f;

    int x_px = (int) (normalizedX * depthWidth);
    if (x_px < 0) x_px = 0;
    if (x_px >= depthWidth) x_px = depthWidth - 1;

    int startY = depthHeight / 3;
    int endY = (depthHeight * 2) / 3;
    int window = 20;
    int startX = Math.max(0, x_px - window);
    int endX = Math.min(depthWidth - 1, x_px + window);
    float minValidDist = Float.MAX_VALUE;

    for (int y = startY; y < endY; y += 5) {
      for (int x = startX; x <= endX; x += 5) {
        int index = y * depthWidth + x;
        if (index >= 0 && index < depth.length) {
          short d = depth[index];
          float distMeters = (d & 0xFFFF) / 1000f;
          if (distMeters >= ASTRA_MIN_DIST_M && distMeters <= ASTRA_MAX_DIST_M) {
            if (distMeters < minValidDist) {
              minValidDist = distMeters;
            }
          }
        }
      }
    }

    if (minValidDist != Float.MAX_VALUE) {
      return minValidDist;
    }
    return -1f;
  }

  private void checkLostTimeout() {
    if (System.currentTimeMillis() - lastDetectionTime > LOST_TIMEOUT_MS) {
      if (listener != null) {
        listener.onPersonLost();
      }
    }
  }

  public void stop() {
    tflitePoseProcessor.release();
  }
}
