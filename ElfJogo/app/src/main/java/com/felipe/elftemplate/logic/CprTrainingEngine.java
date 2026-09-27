package com.felipe.elftemplate.logic;

/**
 * Motor do treino de RCP: passos sequenciais, contador de compressões e fase do pulso visual
 * (100–120 BPM, padrão 110).
 */
public class CprTrainingEngine {

  public static final float TARGET_BPM = 110f;
  private static final float BEAT_INTERVAL_SEC = 60f / TARGET_BPM;

  private int stepIndex;
  private int compressionCount;
  private float beatTimerSec;
  private boolean compressPhase;
  private boolean releaseSeen;
  private float pulseScale;

  public CprTrainingEngine() {
    reset();
  }

  public void reset() {
    stepIndex = 0;
    compressionCount = 0;
    beatTimerSec = 0f;
    compressPhase = true;
    releaseSeen = false;
    pulseScale = 1f;
  }

  public CprStep getCurrentStep() {
    return CprStep.values()[stepIndex];
  }

  public int getStepIndex() {
    return stepIndex;
  }

  public int getStepCount() {
    return CprStep.values().length;
  }

  public boolean advanceStep() {
    if (stepIndex >= CprStep.values().length - 1) {
      return false;
    }
    stepIndex++;
    beatTimerSec = 0f;
    compressPhase = true;
    releaseSeen = false;
    return true;
  }

  public boolean previousStep() {
    if (stepIndex <= 0) {
      return false;
    }
    stepIndex--;
    beatTimerSec = 0f;
    compressPhase = true;
    releaseSeen = false;
    return true;
  }

  public void goToStep(int index) {
    if (index < 0 || index >= CprStep.values().length) {
      return;
    }
    stepIndex = index;
    beatTimerSec = 0f;
    compressPhase = true;
    releaseSeen = false;
  }

  public int getCompressionCount() {
    return compressionCount;
  }

  public boolean isCompressPhase() {
    return compressPhase;
  }

  public float getPulseScale() {
    return pulseScale;
  }

  /** Avança animação do pulso; retorna true se houve troca de fase (batida). */
  public boolean update(float deltaSec, boolean metronomeActive) {
    if (!metronomeActive || !getCurrentStep().isCompressionPhase()) {
      pulseScale = 1f;
      return false;
    }

    beatTimerSec += deltaSec;
    if (beatTimerSec < BEAT_INTERVAL_SEC) {
      float t = beatTimerSec / BEAT_INTERVAL_SEC;
      if (compressPhase) {
        pulseScale = 1f + 0.35f * t;
      } else {
        pulseScale = 1.35f - 0.35f * t;
      }
      return false;
    }

    beatTimerSec -= BEAT_INTERVAL_SEC;
    compressPhase = !compressPhase;
    if (compressPhase) {
      pulseScale = 1f;
      if (releaseSeen) {
        compressionCount++;
      }
    } else {
      pulseScale = 1.35f;
      releaseSeen = true;
    }
    return true;
  }
}
