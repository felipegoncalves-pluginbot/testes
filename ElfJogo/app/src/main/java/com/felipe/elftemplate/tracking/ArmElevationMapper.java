package com.felipe.elftemplate.tracking;

/**
 * Mapeia elevação normalizada (0..1) ao ângulo absoluto das asas Sanbot.
 *
 * <p>SDK Sanbot (sanbotsdk.md §3.5.3): {@code AbsoluteAngleWingMotion} usa 0–270°
 * (anti-horário). Documentação oficial: RESET = vertical para baixo (0°); exemplo de
 * posição horizontal = 90° (HandUSBCommand moveHandLSBDegree=90).
 */
public final class ArmElevationMapper {

  /** Braço abaixado / posição RESET (vertical para baixo). */
  public static final int WING_ANGLE_MIN = 0;
  /** Braço horizontal — referência oficial do SDK para posição absoluta. */
  public static final int WING_ANGLE_MAX = 90;

  private ArmElevationMapper() {}

  public static int elevationToWingAngle(float elevation) {
    float t = elevation;
    if (t < 0f) {
      t = 0f;
    }
    if (t > 1f) {
      t = 1f;
    }
    return WING_ANGLE_MIN + (int) (t * (WING_ANGLE_MAX - WING_ANGLE_MIN));
  }
}
