package com.felipe.elftemplate.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HeadGazeServoTest {

  /**
   * Onde o jogador aparece na imagem de uma câmera presa na cabeça, com a cabeça em {@code yaw}.
   */
  private static float imageX(float playerBearingDeg, float headYawDeg) {
    return 0.5f - ((playerBearingDeg - headYawDeg) / HeadGazeServo.DEPTH_FOV_H_DEG);
  }

  @Test
  public void playerInsideTheCentralZoneKeepsTheHeadStill() {
    HeadGazeServo servo = new HeadGazeServo();
    assertEquals(0, servo.update(0.5f, 0L));
    assertEquals(0, servo.update(0.5f + HeadGazeServo.DEADZONE_X * 0.9f, 1000L));
    assertEquals(0, servo.update(0.5f - HeadGazeServo.DEADZONE_X * 0.9f, 2000L));
  }

  @Test
  public void stepDirectionFollowsTheCoordinateContract() {
    HeadGazeServo cameraLeft = new HeadGazeServo();
    HeadGazeServo cameraRight = new HeadGazeServo();
    assertTrue("X < 0,5 → yaw positivo (hardware > 90°)", cameraLeft.update(0.25f, 0L) > 0);
    assertTrue("X > 0,5 → yaw negativo (hardware < 90°)", cameraRight.update(0.75f, 0L) < 0);
  }

  @Test
  public void nextStepWaitsForTheHeadToSettle() {
    HeadGazeServo servo = new HeadGazeServo();
    int first = servo.update(0.20f, 0L);
    assertEquals(first, servo.update(0.20f, HeadGazeServo.SETTLE_MS - 1));
    assertTrue(
        "depois do settle anda de novo", servo.update(0.20f, HeadGazeServo.SETTLE_MS) > first);
  }

  @Test
  public void stepsAndRangeAreBoundedByTheHardware() {
    HeadGazeServo servo = new HeadGazeServo();
    int first = servo.update(0f, 0L);
    assertTrue("um passo nunca passa do limite", first <= HeadGazeServo.MAX_STEP_DEG);
    for (int i = 1; i < 20; i++) {
      servo.update(0f, i * HeadGazeServo.SETTLE_MS);
    }
    assertEquals("yaw preso à faixa segura", (int) HeadGazeServo.MAX_YAW_DEG, servo.getYawOffset());
  }

  /**
   * Malha fechada: a imagem depende do yaw comandado. O mapa absoluto antigo, (0,5 − x)·60,
   * oscilava aqui; o servo tem que centrar o jogador sem nunca inverter o sentido dos passos.
   */
  @Test
  public void closedLoopCentersThePlayerWithoutOscillating() {
    HeadGazeServo servo = new HeadGazeServo();
    float bearing = 25f;
    int previous = 0;
    for (int i = 0; i < 12; i++) {
      int yaw = servo.update(imageX(bearing, previous), i * HeadGazeServo.SETTLE_MS);
      assertTrue("passos sempre no mesmo sentido", yaw >= previous);
      previous = yaw;
    }
    float finalX = imageX(bearing, previous);
    assertTrue(
        "jogador dentro da zona central: " + finalX,
        Math.abs(finalX - 0.5f) <= HeadGazeServo.DEADZONE_X);
  }

  @Test
  public void resetReturnsToCenterAndAllowsAnImmediateStep() {
    HeadGazeServo servo = new HeadGazeServo();
    servo.update(0.2f, 0L);
    servo.reset();
    assertEquals(0, servo.getYawOffset());
    assertTrue("sem espera depois do reset", servo.update(0.2f, 1L) > 0);
  }
}
