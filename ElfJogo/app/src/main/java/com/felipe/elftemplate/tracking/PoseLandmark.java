package com.felipe.elftemplate.tracking;

import android.graphics.PointF;

/** Mock para substituir o ML Kit PoseLandmark. */
public class PoseLandmark {
  public static final int NOSE = 0;
  public static final int LEFT_SHOULDER = 1;
  public static final int RIGHT_SHOULDER = 2;
  public static final int LEFT_ELBOW = 3;
  public static final int RIGHT_ELBOW = 4;
  public static final int LEFT_WRIST = 5;
  public static final int RIGHT_WRIST = 6;
  public static final int LEFT_HIP = 7;
  public static final int RIGHT_HIP = 8;

  public PointF getPosition() {
    return new PointF(0, 0);
  }

  public float getInFrameLikelihood() {
    return 0f;
  }
}
