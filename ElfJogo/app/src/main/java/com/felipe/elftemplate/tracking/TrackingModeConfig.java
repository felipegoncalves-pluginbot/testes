package com.felipe.elftemplate.tracking;

/** Configuração de quantas pessoas rastrear e como escolher o jogador principal. */
public class TrackingModeConfig {

  private TrackingMode mode = TrackingMode.SINGLE_CLOSEST;
  private int maxPersons = 2;
  private int stickyBlobLabel = -1;
  private int stickyLostFrames = 0;
  private static final int STICKY_RELEASE_FRAMES = 15;

  public TrackingMode getMode() {
    return mode;
  }

  public void setMode(TrackingMode mode) {
    if (mode != null) {
      this.mode = mode;
    }
  }

  public int getMaxPersons() {
    return maxPersons;
  }

  public void setMaxPersons(int maxPersons) {
    this.maxPersons = Math.max(1, Math.min(6, maxPersons));
  }

  public int getStickyBlobLabel() {
    return stickyBlobLabel;
  }

  public void resetStickyLock() {
    stickyBlobLabel = -1;
    stickyLostFrames = 0;
  }

  void updateStickyLock(int label) {
    if (label >= 0) {
      stickyBlobLabel = label;
      stickyLostFrames = 0;
    } else {
      stickyLostFrames++;
      if (stickyLostFrames >= STICKY_RELEASE_FRAMES) {
        stickyBlobLabel = -1;
      }
    }
  }
}
