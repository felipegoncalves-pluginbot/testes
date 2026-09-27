package com.felipe.elftemplate.architecture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.felipe.elftemplate.logic.MirrorGameEngine;
import com.felipe.elftemplate.movement.RobotGameFeedback;
import com.felipe.elftemplate.tracking.TrackingResult;
import org.junit.Before;
import org.junit.Test;

/**
 * Teste Guard de Arquitetura e Cinemática Física da Cabeça e Atuadores do Sanbot Elf.
 * Garante que a orientação angular do hardware, a suavização temporal (EMA) e
 * os limites mecânicos nunca sejam corrompidos por IA ou alterações acidentais.
 */
public class HeadKinematicsGuardTest {

  private MirrorGameEngine mirrorEngine;

  @Before
  public void setUp() {
    mirrorEngine = new MirrorGameEngine();
  }

  @Test
  public void testUserMovingRightDirectsRobotHeadTowardsUserRight() {
    TrackingResult result = new TrackingResult();
    result.isPlayerPresent = true;
    // Usuário desloca-se para a sua DIREITA (no sensor de profundidade, x < 0.5).
    // No hardware Sanbot Elf, virar para a direita do usuário exige ângulo > 90° (yawOffset positivo).
    result.head.x = 0.25f;
    result.head.y = 0.30f;

    for (int i = 0; i < 5; i++) {
      mirrorEngine.processTracking(result);
    }

    int targetYawOffset = mirrorEngine.getTargetHeadYaw();
    int hardwareYaw = RobotGameFeedback.calculateHardwareYaw(targetYawOffset);

    assertTrue(
        "Quando o usuário vai para a direita (x < 0.5), o offset de yaw deve ser POSITIVO para apontar para a direita do usuário",
        targetYawOffset > 0);
    assertTrue(
        "No hardware Sanbot Elf, a cabeça deve girar para a direita do usuário (> 90°)",
        hardwareYaw > 90 && hardwareYaw <= 150);
  }

  @Test
  public void testUserMovingLeftDirectsRobotHeadTowardsUserLeft() {
    TrackingResult result = new TrackingResult();
    result.isPlayerPresent = true;
    // Usuário desloca-se para a sua ESQUERDA (no sensor de profundidade, x > 0.5).
    // No hardware Sanbot Elf, virar para a esquerda do usuário exige ângulo < 90° (yawOffset negativo).
    result.head.x = 0.75f;
    result.head.y = 0.30f;

    for (int i = 0; i < 5; i++) {
      mirrorEngine.processTracking(result);
    }

    int targetYawOffset = mirrorEngine.getTargetHeadYaw();
    int hardwareYaw = RobotGameFeedback.calculateHardwareYaw(targetYawOffset);

    assertTrue(
        "Quando o usuário vai para a esquerda (x > 0.5), o offset de yaw deve ser NEGATIVO para apontar para a esquerda do usuário",
        targetYawOffset < 0);
    assertTrue(
        "No hardware Sanbot Elf, a cabeça deve girar para a esquerda do usuário (< 90°)",
        hardwareYaw < 90 && hardwareYaw >= 30);
  }

  @Test
  public void testExponentialMovingAverageDampensAbruptCoordinateJumps() {
    TrackingResult result = new TrackingResult();
    result.isPlayerPresent = true;
    result.head.x = 0.50f;
    result.head.y = 0.30f;

    // Inicializa no centro
    mirrorEngine.processTracking(result);
    assertEquals(0, mirrorEngine.getTargetHeadYaw());

    // Salto abrupto de 1 quadro (ruído na câmera para x = 0.10)
    result.head.x = 0.10f;
    mirrorEngine.processTracking(result);

    int firstFrameYaw = mirrorEngine.getTargetHeadYaw();
    // Com filtro EMA, no primeiro frame o yaw converge suavemente (positivo para x < 0.5)
    assertTrue(
        "Filtro EMA deve suavizar a aceleração angular no primeiro frame",
        firstFrameYaw > 0 && firstFrameYaw < 22);

    // Quadros subsequentes convergem suavemente
    for (int i = 0; i < 10; i++) {
      mirrorEngine.processTracking(result);
      int currentYaw = mirrorEngine.getTargetHeadYaw();
      assertTrue("Yaw deve convergir de forma monótona e suave", currentYaw >= firstFrameYaw);
    }
  }

  @Test
  public void testSafetyClampProtectsHardwareLimits() {
    for (int offset = -200; offset <= 200; offset += 10) {
      int hwYaw = RobotGameFeedback.calculateHardwareYaw(offset);
      int hwPitch = RobotGameFeedback.calculateHardwarePitch(offset);

      assertTrue(
          "Hardware Yaw deve respeitar faixa segura [30, 150]",
          hwYaw >= RobotGameFeedback.MIN_SAFE_HARDWARE_YAW
              && hwYaw <= RobotGameFeedback.MAX_SAFE_HARDWARE_YAW);
      assertTrue(
          "Hardware Pitch deve respeitar faixa segura [10, 28]",
          hwPitch >= RobotGameFeedback.MIN_SAFE_HARDWARE_PITCH
              && hwPitch <= RobotGameFeedback.MAX_SAFE_HARDWARE_PITCH);
    }
  }
}
