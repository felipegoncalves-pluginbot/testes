package com.felipe.elftemplate.movement;

import com.sanbot.opensdk.function.beans.headmotion.LocateAbsoluteAngleHeadMotion;
import com.sanbot.opensdk.function.unit.HeadMotionManager;

/**
 * Controlador de movimentação e conversão de ângulos de hardware da cabeça do Sanbot Elf.
 * Aplica comandos em modo destravado (ACTION_NO_LOCK) durante o rastreamento em tempo real
 * para garantir movimentos contínuos, silenciosos e fluidos sem trancos de frenagem.
 */
public class RobotHeadController {

  public static final int SANBOT_HEAD_CENTER_YAW = 90;
  public static final int SANBOT_HEAD_LEVEL_PITCH = 18;
  public static final int MIN_SAFE_HARDWARE_YAW = 30;
  public static final int MAX_SAFE_HARDWARE_YAW = 150;
  public static final int MIN_SAFE_HARDWARE_PITCH = 10;
  public static final int MAX_SAFE_HARDWARE_PITCH = 28;

  private final HeadMotionManager headManager;
  private int lastHardwareYaw = SANBOT_HEAD_CENTER_YAW;
  private int lastHardwarePitch = SANBOT_HEAD_LEVEL_PITCH;
  private long lastHeadUpdateTime = 0;

  public RobotHeadController(HeadMotionManager headManager) {
    this.headManager = headManager;
  }

  public static int calculateHardwareYaw(int yawOffset) {
    int yaw = SANBOT_HEAD_CENTER_YAW + yawOffset;
    if (yaw < MIN_SAFE_HARDWARE_YAW) yaw = MIN_SAFE_HARDWARE_YAW;
    if (yaw > MAX_SAFE_HARDWARE_YAW) yaw = MAX_SAFE_HARDWARE_YAW;
    return yaw;
  }

  public static int calculateHardwarePitch(int pitchOffset) {
    int pitch = SANBOT_HEAD_LEVEL_PITCH + pitchOffset;
    if (pitch < MIN_SAFE_HARDWARE_PITCH) pitch = MIN_SAFE_HARDWARE_PITCH;
    if (pitch > MAX_SAFE_HARDWARE_PITCH) pitch = MAX_SAFE_HARDWARE_PITCH;
    return pitch;
  }

  public void updateMirrorHeadAngle(int targetYawOffset, int targetPitchOffset) {
    long now = System.currentTimeMillis();
    // Limite de taxa de envio de 80ms para não sobrecarregar o barramento CAN/UART
    if (now - lastHeadUpdateTime < 80) return;

    int hwYaw = calculateHardwareYaw(targetYawOffset);
    int hwPitch = calculateHardwarePitch(targetPitchOffset);

    // Envia comando apenas se houver variação angular relevante (elimina tremor)
    if (Math.abs(hwYaw - lastHardwareYaw) >= 2 || Math.abs(hwPitch - lastHardwarePitch) >= 2) {
      lastHeadUpdateTime = now;
      lastHardwareYaw = hwYaw;
      lastHardwarePitch = hwPitch;
      if (headManager != null) {
        // ACTION_NO_LOCK permite que o servo se mova suavemente sem engatar o freio magnético
        LocateAbsoluteAngleHeadMotion motion =
            new LocateAbsoluteAngleHeadMotion(
                LocateAbsoluteAngleHeadMotion.ACTION_NO_LOCK, hwYaw, hwPitch);
        headManager.doAbsoluteLocateMotion(motion);
      }
    }
  }

  public void resetCenter() {
    lastHardwareYaw = SANBOT_HEAD_CENTER_YAW;
    lastHardwarePitch = SANBOT_HEAD_LEVEL_PITCH;
    if (headManager != null) {
      LocateAbsoluteAngleHeadMotion motion =
          new LocateAbsoluteAngleHeadMotion(
              LocateAbsoluteAngleHeadMotion.ACTION_BOTH_LOCK,
              SANBOT_HEAD_CENTER_YAW,
              SANBOT_HEAD_LEVEL_PITCH);
      headManager.doAbsoluteLocateMotion(motion);
    }
  }
}
