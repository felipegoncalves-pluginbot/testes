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
  private static final float HEAD_PITCH_DEADZONE = 0.04f;
  private static final float WING_ELEV_DEADZONE = 0.07f;
  private static final float HEAD_EMA_ALPHA = 0.35f;

  private boolean leftWingUp = false;
  private boolean rightWingUp = false;
  private int targetLeftWingAngle = ArmElevationMapper.WING_ANGLE_MIN;
  private int targetRightWingAngle = ArmElevationMapper.WING_ANGLE_MIN;
  private int targetHeadYaw = 0;
  private int targetHeadPitch = 0;

  private float smoothedYaw = 0.0f;
  private float smoothedPitch = 0.0f;
  private boolean headInitialized = false;

  private boolean lastLeftWingLogged = false;
  private boolean lastRightWingLogged = false;

  public void processTracking(TrackingResult result) {
    if (result == null || !result.isPlayerPresent) {
      leftWingUp = false;
      rightWingUp = false;
      targetLeftWingAngle = ArmElevationMapper.WING_ANGLE_MIN;
      targetRightWingAngle = ArmElevationMapper.WING_ANGLE_MIN;
      targetHeadYaw = 0;
      targetHeadPitch = 0;
      smoothedYaw = 0.0f;
      smoothedPitch = 0.0f;
      headInitialized = false;
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

    mapHeadAngles(result);
  }

  private void mapHeadAngles(TrackingResult result) {
    float headNormX = result.head != null ? result.head.x : result.playerCentroidX;
    float headNormY = result.head != null ? result.head.y : result.playerCentroidY;
    float spineNormX = result.spine != null ? result.spine.x : result.playerCentroidX;

    float deltaX;
    boolean usePoseFusion = result.diagnostics != null && result.diagnostics.isPoseFusionActive;
    if (!usePoseFusion && result.diagnostics != null && result.diagnostics.isSeatedPose) {
      float relativeTurn = headNormX - spineNormX;
      float absoluteShift = headNormX - 0.5f;
      deltaX = (relativeTurn * 2.2f) + (absoluteShift * 0.35f);
    } else {
      deltaX = headNormX - 0.5f;
    }

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

    float deltaY = 0.30f - headNormY;
    float rawTargetPitch;
    if (Math.abs(deltaY) <= HEAD_PITCH_DEADZONE) {
      rawTargetPitch = 0.0f;
    } else {
      float signY = deltaY > 0 ? 1.0f : -1.0f;
      rawTargetPitch = (deltaY - signY * HEAD_PITCH_DEADZONE) * 55.0f;
      if (rawTargetPitch > 25.0f) rawTargetPitch = 25.0f;
      if (rawTargetPitch < -20.0f) rawTargetPitch = -20.0f;
    }

    if (!headInitialized) {
      smoothedYaw = rawTargetYaw;
      smoothedPitch = rawTargetPitch;
      headInitialized = true;
    } else {
      smoothedYaw = smoothedYaw + HEAD_EMA_ALPHA * (rawTargetYaw - smoothedYaw);
      smoothedPitch = smoothedPitch + HEAD_EMA_ALPHA * (rawTargetPitch - smoothedPitch);
    }

    this.targetHeadYaw = Math.round(smoothedYaw);
    this.targetHeadPitch = Math.round(smoothedPitch);
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

  public int getTargetHeadPitch() {
    return targetHeadPitch;
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
