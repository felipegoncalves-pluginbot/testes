package com.felipe.elftemplate.tracking;

/**
 * Ponto-chave anatômico normalizado [0.0, 1.0] com índice de confiança (MoveNet / TFLite).
 * Mutável de propósito: o hot path reutiliza 17 instâncias pré-alocadas.
 */
public class PoseLandmarkData {
  public float x;
  public float y;
  public float score;

  public PoseLandmarkData(float x, float y, float score) {
    set(x, y, score);
  }

  public void set(float x, float y, float score) {
    this.x = x;
    this.y = y;
    this.score = score;
  }

  static PoseLandmarkData[] allocateKeypoints() {
    PoseLandmarkData[] points = new PoseLandmarkData[PoseFrame.KEYPOINT_COUNT];
    for (int i = 0; i < points.length; i++) {
      points[i] = new PoseLandmarkData(0f, 0f, 0f);
    }
    return points;
  }
}
