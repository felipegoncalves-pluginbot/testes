package com.felipe.elftemplate.tracking;

/** Wrapper para rastreamento (Mock - ML Kit Removido). Detecta o esqueleto do jogador e gestos. */
public class BodyPoseTracker {
  public interface PoseListener {
    void onSwingDetected();
  }

  public interface PoseUpdateListener {
    void onPoseUpdated(Pose pose);
  }

  private PoseListener listener;
  private PoseUpdateListener poseUpdateListener;
  private float lastHandY = 0;
  private long lastSwingTime = 0;
  private boolean isProcessing = false;
  private int frameCounter = 0;

  public BodyPoseTracker(PoseListener listener) {
    this.listener = listener;
  }

  public void setPoseUpdateListener(PoseUpdateListener poseUpdateListener) {
    this.poseUpdateListener = poseUpdateListener;
  }

  public void processFrame(byte[] yuvData, int width, int height) {
    // Mock: ML Kit foi desativado por causar crash.
  }

  private void analyzePose(Pose pose) {
    if (pose == null) return;
    if (poseUpdateListener != null) {
      poseUpdateListener.onPoseUpdated(pose);
    }

    PoseLandmark rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST);
    PoseLandmark leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST);

    if (rightWrist != null) {
      detectSwing(rightWrist.getPosition().y);
    } else if (leftWrist != null) {
      detectSwing(leftWrist.getPosition().y);
    }
  }

  private void detectSwing(float currentHandY) {
    if (lastHandY == 0) {
      lastHandY = currentHandY;
      return;
    }
    float deltaY = lastHandY - currentHandY; // Y diminui para cima
    // Aumenta sensibilidade (40 em vez de 50) e diminui cooldown (300ms)
    if (deltaY > 40 && (System.currentTimeMillis() - lastSwingTime) > 300) {
      lastSwingTime = System.currentTimeMillis();
      if (listener != null) {
        listener.onSwingDetected();
      }
    }
    lastHandY = currentHandY;
  }
}
