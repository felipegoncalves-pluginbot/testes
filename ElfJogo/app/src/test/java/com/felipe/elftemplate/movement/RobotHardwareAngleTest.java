package com.felipe.elftemplate.movement;

import static org.junit.Assert.*;

import com.felipe.elftemplate.movement.RobotGameFeedback;
import org.junit.Test;

/** Testes Unitários TDD para o Mapeamento Angular de Hardware do Sanbot Elf (Head Motion). */
public class RobotHardwareAngleTest {

  @Test
  public void testCenterOffsetMapsToSanbotCenterAngles() {
    int yawOffset = 0;
    int pitchOffset = 0;

    int hardwareYaw = RobotGameFeedback.calculateHardwareYaw(yawOffset);
    int hardwarePitch = RobotGameFeedback.calculateHardwarePitch(pitchOffset);

    assertEquals("Offset 0 em Yaw deve mapear para o centro do Sanbot (90°)", 90, hardwareYaw);
    assertEquals(
        "Offset 0 em Pitch deve mapear para o nível neutro do Sanbot (18°)", 18, hardwarePitch);

    for (int offset = -10; offset <= 10; offset += 5) {
      int y = RobotGameFeedback.calculateHardwareYaw(offset);
      assertTrue("Yaw deve estar entre 30 e 150", y >= 30 && y <= 150);
    }
  }

  @Test
  public void testLateralOffsetsMapWithinSafeHardwareRange() {
    // Offset positivo (+30°) -> 90 + 30 = 120°
    int rightYaw = RobotGameFeedback.calculateHardwareYaw(30);
    assertEquals("Offset +30° deve mapear para 120°", 120, rightYaw);

    // Offset negativo (-30°) -> 90 - 30 = 60°
    int leftYaw = RobotGameFeedback.calculateHardwareYaw(-30);
    assertEquals("Offset -30° deve mapear para 60°", 60, leftYaw);

    // Offset vertical (+8°) -> 18 + 8 = 26°
    int upPitch = RobotGameFeedback.calculateHardwarePitch(8);
    assertEquals("Offset +8° deve mapear para 26°", 26, upPitch);

    // Offset vertical (-8°) -> 18 - 8 = 10°
    int downPitch = RobotGameFeedback.calculateHardwarePitch(-8);
    assertEquals("Offset -8° deve mapear para 10°", 10, downPitch);
  }

  @Test
  public void testHardwareClampingProtectsMotors() {
    // Teste de clamp para Yaw (faixa segura: 30° a 150°)
    int extremeRightYaw = RobotGameFeedback.calculateHardwareYaw(100);
    assertEquals("Offset excessivo para a direita deve sofrer clamp em 150°", 150, extremeRightYaw);

    int extremeLeftYaw = RobotGameFeedback.calculateHardwareYaw(-100);
    assertEquals("Offset excessivo para a esquerda deve sofrer clamp em 30°", 30, extremeLeftYaw);

    // Teste de clamp para Pitch (faixa segura: 10° a 28°)
    int extremeUpPitch = RobotGameFeedback.calculateHardwarePitch(50);
    assertEquals("Offset excessivo para cima deve sofrer clamp em 28°", 28, extremeUpPitch);

    int extremeDownPitch = RobotGameFeedback.calculateHardwarePitch(-50);
    assertEquals("Offset excessivo para baixo deve sofrer clamp em 10°", 10, extremeDownPitch);
  }
}
