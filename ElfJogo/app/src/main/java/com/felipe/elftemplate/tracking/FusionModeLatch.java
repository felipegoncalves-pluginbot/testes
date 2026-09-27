package com.felipe.elftemplate.tracking;

/**
 * Histerese da fusão RGB+depth. Evita o esqueleto teleportar quando o MoveNet
 * perde o corpo 1–2 frames (luz, contraste). Enter 2 / exit 4 — mesmo padrão
 * Schmitt do classificador de gestos.
 */
final class FusionModeLatch {

  static final int ENTER_FRAMES = 2;
  static final int EXIT_FRAMES = 4;

  private int goodStreak = 0;
  private int badStreak = 0;
  private boolean latched = false;

  boolean observe(boolean fusedNow) {
    if (fusedNow) {
      goodStreak++;
      badStreak = 0;
      if (goodStreak >= ENTER_FRAMES) {
        latched = true;
      }
    } else {
      badStreak++;
      goodStreak = 0;
      if (badStreak >= EXIT_FRAMES) {
        latched = false;
      }
    }
    return latched;
  }

  boolean isLatched() {
    return latched;
  }

  void reset() {
    goodStreak = 0;
    badStreak = 0;
    latched = false;
  }
}
