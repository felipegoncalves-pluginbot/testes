package com.felipe.elftemplate.tracking;

/**
 * Reutiliza o último pose válido quando a luz cai 1–2 frames (evento / palco).
 */
final class PoseFrameHold {

  static final long MAX_AGE_MS = 550;

  private final PoseLandmarkData[] snapshot = PoseLandmarkData.allocateKeypoints();
  private long savedMs;

  void save(PoseLandmarkData[] src) {
    if (src == null || src.length < PoseFrame.KEYPOINT_COUNT) {
      return;
    }
    for (int i = 0; i < PoseFrame.KEYPOINT_COUNT; i++) {
      snapshot[i].set(src[i].x, src[i].y, src[i].score);
    }
    savedMs = System.currentTimeMillis();
  }

  boolean applyTo(PoseLandmarkData[] dst) {
    if (savedMs <= 0 || dst == null || dst.length < PoseFrame.KEYPOINT_COUNT) {
      return false;
    }
    long age = System.currentTimeMillis() - savedMs;
    if (age > MAX_AGE_MS) {
      return false;
    }
    float decay = 1f - age / (float) MAX_AGE_MS * 0.12f;
    for (int i = 0; i < PoseFrame.KEYPOINT_COUNT; i++) {
      dst[i].set(snapshot[i].x, snapshot[i].y, snapshot[i].score * decay);
    }
    return true;
  }

  void clear() {
    savedMs = 0;
  }
}
