package com.felipe.elftemplate.movement;

import android.util.Log;

/** Garante que o robô não ultrapasse limites de segurança. */
public class MovementSafetyGuard {
  private static final float MAX_TOTAL_DISPLACEMENT = 100.0f; // Exemplo em cm
  private float currentDisplacement = 0;

  /**
   * Valida se um movimento para frente é seguro.
   *
   * @param distance Distância solicitada.
   * @return true se permitido.
   */
  public boolean canMoveForward(float distance) {
    if (currentDisplacement + distance > MAX_TOTAL_DISPLACEMENT) {
      Log.w("SafetyGuard", "Limite de movimento atingido!");
      return false;
    }
    return true;
  }

  public void registerMovement(float distance) {
    currentDisplacement += distance;
  }

  public void reset() {
    currentDisplacement = 0;
  }

  public float getCurrentDisplacement() {
    return currentDisplacement;
  }
}
