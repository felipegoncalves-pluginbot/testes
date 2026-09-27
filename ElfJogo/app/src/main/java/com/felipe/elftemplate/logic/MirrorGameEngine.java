package com.felipe.elftemplate.logic;

import android.util.Log;
import com.felipe.elftemplate.tracking.ArmElevationMapper;
import com.felipe.elftemplate.tracking.TrackingResult;

/**
 * Motor de Cálculo Cinemático de Espelhamento e Marionete em Tempo Real (Mirror Puppet Engine).
 * Traduz movimentos articulares do jogador em comandos físicos para o Sanbot Elf.
 */
public class MirrorGameEngine {
  private static final String TAG = "MirrorGameEngine";

  private static final float HEAD_YAW_DEADZONE = 0.05f;
  private static final float WING_ELEV_DEADZONE = 0.07f;
  private static final float HEAD_EMA_ALPHA = 0.35f;

  private boolean leftWingUp = false;
  private boolean rightWingUp = false;
  private int targetLeftWingAngle = ArmElevationMapper.WING_ANGLE_MIN;
  private int targetRightWingAngle = ArmElevationMapper.WING_ANGLE_MIN;
  private int targetHeadYaw = 0;

  /** Olhar para o jogador em pé: o Astra está na cabeça, então o yaw é servo, não mapa absoluto. */
  private final HeadGazeServo gazeServo = new HeadGazeServo();

  private float smoothedYaw = 0.0f;
  private boolean headInitialized = false;

  private boolean lastLeftWingLogged = false;
  private boolean lastRightWingLogged = false;

  public void processTracking(TrackingResult result) {
    processTracking(result, System.currentTimeMillis());
  }

  /** Igual a {@link #processTracking(TrackingResult)}, com o relógio explícito (testes). */
  public void processTracking(TrackingResult result, long nowMs) {
    if (result == null || !result.isPlayerPresent) {
      leftWingUp = false;
      rightWingUp = false;
      targetLeftWingAngle = ArmElevationMapper.WING_ANGLE_MIN;
      targetRightWingAngle = ArmElevationMapper.WING_ANGLE_MIN;
      targetHeadYaw = 0;
      smoothedYaw = 0.0f;
      headInitialized = false;
      gazeServo.reset();
      return;
    }

    // Espelho frontal direto: lado esquerdo da câmera (mão D do usuário) → asa esquerda do robô
    float leftElev = applyElevationDeadzone(result.leftHandElevation);
    float rightElev = applyElevationDeadzone(result.rightHandElevation);

    int rawLeftAngle = ArmElevationMapper.elevationToWingAngle(leftElev);
    int rawRightAngle = ArmElevationMapper.elevationToWingAngle(rightElev);
    this.leftWingUp = result.isLeftHandRaised || rawLeftAngle >= 25;
    this.rightWingUp = result.isRightHandRaised || rawRightAngle >= 25;
    // Um smoother só: ArmElevationTracker (MediaPipe One-Euro / Kinect white paper).
    // MIRROR_UPDATE_MIN_MS + deadband 4° no servo; sem segundo EMA aqui.
    targetLeftWingAngle = rawLeftAngle;
    targetRightWingAngle = rawRightAngle;

    if (this.leftWingUp != lastLeftWingLogged || this.rightWingUp != lastRightWingLogged) {
      Log.i(
          TAG,
          String.format(
              "[MIRROR-LOGIC] Asas -> Esq: %s (elev=%.2f ang=%d) | Dir: %s (elev=%.2f ang=%d)",
              (this.leftWingUp ? "LEVANTADA" : "ABAIXADA"),
              leftElev,
              targetLeftWingAngle,
              (this.rightWingUp ? "LEVANTADA" : "ABAIXADA"),
              rightElev,
              targetRightWingAngle));
      lastLeftWingLogged = this.leftWingUp;
      lastRightWingLogged = this.rightWingUp;
    }

    mapHeadYaw(result, nowMs);
  }

  /**
   * Yaw da cabeça. O pitch não é comandado: o Astra está na cabeça, e inclinar a cabeça invalida o
   * plano do chão até ele ser reestimado. No sintético, 6° para baixo tiram o jogador por 4
   * reestimativas (~120 frames).
   *
   * <p>Em pé, a cabeça só acompanha o jogador ({@link HeadGazeServo}). O mapa absoluto antigo,
   * {@code yaw = −60·(x − 0,5)}, pressupunha câmera fixa: com ela na cabeça, a malha deixava o
   * jogador a 20% do centro (com EMA) ou oscilava sem parar (sem EMA, como nos jogos).
   *
   * <p>Sentado, a cabeça imita o giro da cabeça do usuário em relação ao tronco. Essa diferença não
   * muda quando a câmera gira, e o termo absoluto tem ganho 0,35: a malha fica estável com a câmera
   * na cabeça, então esse caminho continua como era.
   */
  private void mapHeadYaw(TrackingResult result, long nowMs) {
    float headNormX = result.head != null ? result.head.x : result.playerCentroidX;
    float spineNormX = result.spine != null ? result.spine.x : result.playerCentroidX;

    boolean usePoseFusion = result.diagnostics != null && result.diagnostics.isPoseFusionActive;
    boolean seatedMimic =
        !usePoseFusion && result.diagnostics != null && result.diagnostics.isSeatedPose;
    if (!seatedMimic) {
      if (headInitialized) {
        gazeServo.syncTo(smoothedYaw);
        headInitialized = false;
      }
      this.targetHeadYaw = gazeServo.update(headNormX, nowMs);
      return;
    }

    float relativeTurn = headNormX - spineNormX;
    float absoluteShift = headNormX - 0.5f;
    float deltaX = (relativeTurn * 2.2f) + (absoluteShift * 0.35f);
    float rawTargetYaw;
    if (Math.abs(deltaX) <= HEAD_YAW_DEADZONE) {
      rawTargetYaw = 0.0f;
    } else {
      float signX = deltaX > 0 ? 1.0f : -1.0f;
      // Sanbot Elf hardware: < 90° vira para a esquerda do usuário, > 90° vira para a direita.
      // Usuário em deltaX > 0 (esquerda do usuário na câmera) requer targetYaw negativo (< 90°).
      rawTargetYaw = (deltaX - signX * HEAD_YAW_DEADZONE) * -60.0f;
      if (rawTargetYaw > 45.0f) rawTargetYaw = 45.0f;
      if (rawTargetYaw < -45.0f) rawTargetYaw = -45.0f;
    }

    if (!headInitialized) {
      smoothedYaw = gazeServo.getYawOffset();
      headInitialized = true;
    }
    smoothedYaw = smoothedYaw + HEAD_EMA_ALPHA * (rawTargetYaw - smoothedYaw);
    this.targetHeadYaw = Math.round(smoothedYaw);
  }

  private static float applyElevationDeadzone(float elevation) {
    if (elevation < WING_ELEV_DEADZONE) {
      return 0f;
    }
    return elevation;
  }

  public boolean isLeftWingUp() {
    return leftWingUp;
  }

  public boolean isRightWingUp() {
    return rightWingUp;
  }

  public int getTargetLeftWingAngle() {
    return targetLeftWingAngle;
  }

  public int getTargetRightWingAngle() {
    return targetRightWingAngle;
  }

  public int getTargetHeadYaw() {
    return targetHeadYaw;
  }

  public int getTargetHeadAngle() {
    return targetHeadYaw;
  }

  public boolean shouldRotateBase() {
    return false;
  }

  public int getBaseRotationAngle() {
    return 0;
  }
}
