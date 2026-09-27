package com.felipe.elftemplate.tracking;

/**
 * Container dos 17 landmarks do MoveNet Lightning. O processador RGB reutiliza uma instância.
 */
public class PoseFrame {

  public static final int NOSE = 0;
  public static final int LEFT_EYE = 1;
  public static final int RIGHT_EYE = 2;
  public static final int LEFT_EAR = 3;
  public static final int RIGHT_EAR = 4;
  public static final int LEFT_SHOULDER = 5;
  public static final int RIGHT_SHOULDER = 6;
  public static final int LEFT_ELBOW = 7;
  public static final int RIGHT_ELBOW = 8;
  public static final int LEFT_WRIST = 9;
  public static final int RIGHT_WRIST = 10;
  public static final int LEFT_HIP = 11;
  public static final int RIGHT_HIP = 12;
  public static final int LEFT_KNEE = 13;
  public static final int RIGHT_KNEE = 14;
  public static final int LEFT_ANKLE = 15;
  public static final int RIGHT_ANKLE = 16;
  public static final int KEYPOINT_COUNT = 17;

  public PoseLandmarkData[] landmarks;
  public long timestampMs;
  public int frameWidth;
  public int frameHeight;
  /** Média do plano Y NV21 após enhancer. -1 = não medida. */
  public int meanLuma = -1;
  public int lumaSpan = -1;
  /** Pose reutilizado do frame anterior (luz instável). */
  public boolean lightingHold;

  public PoseFrame(
      PoseLandmarkData[] landmarks, long timestampMs, int frameWidth, int frameHeight) {
    this.landmarks = landmarks;
    this.timestampMs = timestampMs;
    this.frameWidth = frameWidth;
    this.frameHeight = frameHeight;
  }

  public void update(long timestampMs, int frameWidth, int frameHeight) {
    this.timestampMs = timestampMs;
    this.frameWidth = frameWidth;
    this.frameHeight = frameHeight;
  }

  public void setLuma(int meanLuma, int lumaSpan) {
    this.meanLuma = meanLuma;
    this.lumaSpan = lumaSpan;
  }

  public void setLightingHold(boolean lightingHold) {
    this.lightingHold = lightingHold;
  }

  public PoseLandmarkData getLandmark(int index) {
    if (landmarks != null && index >= 0 && index < landmarks.length) {
      return landmarks[index];
    }
    return null;
  }

  public boolean hasCoreKeypoints(float minScore) {
    PoseLandmarkData nose = getLandmark(NOSE);
    PoseLandmarkData lShoulder = getLandmark(LEFT_SHOULDER);
    PoseLandmarkData rShoulder = getLandmark(RIGHT_SHOULDER);
    return nose != null && nose.score >= minScore
        && lShoulder != null && lShoulder.score >= minScore
        && rShoulder != null && rShoulder.score >= minScore;
  }
}
