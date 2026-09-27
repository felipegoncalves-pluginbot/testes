package com.felipe.elftemplate.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.felipe.elftemplate.tracking.ArmElevationMapper;
import com.felipe.elftemplate.tracking.TrackingResult;
import org.junit.Before;
import org.junit.Test;

public class MirrorGameEngineTest {

  private MirrorGameEngine engine;

  @Before
  public void setUp() {
    engine = new MirrorGameEngine();
  }

  @Test
  public void testMirroringRightHandRaisesLeftWing() {
    TrackingResult result = new TrackingResult();
    result.isPlayerPresent = true;
    result.leftHandElevation = 0.55f;
    result.rightHandElevation = 0f;
    for (int i = 0; i < 8; i++) {
      engine.processTracking(result);
    }
    assertTrue("Robô deve levantar a asa ESQUERDA", engine.isLeftWingUp());
    assertFalse("Asa direita deve permanecer abaixada", engine.isRightWingUp());
  }

  @Test
  public void testMirroringLeftHandRaisesRightWing() {
    TrackingResult result = new TrackingResult();
    result.isPlayerPresent = true;
    result.leftHandElevation = 0f;
    result.rightHandElevation = 0.55f;
    for (int i = 0; i < 8; i++) {
      engine.processTracking(result);
    }
    assertTrue("Robô deve levantar a asa DIREITA", engine.isRightWingUp());
    assertFalse("Asa esquerda deve permanecer abaixada", engine.isLeftWingUp());
  }

  @Test
  public void testMirroringBothHandsRaisesBothWings() {
    TrackingResult result = new TrackingResult();
    result.isPlayerPresent = true;
    result.leftHandElevation = 0.6f;
    result.rightHandElevation = 0.6f;
    for (int i = 0; i < 8; i++) {
      engine.processTracking(result);
    }
    assertTrue("Ambas as asas devem subir", engine.isLeftWingUp() && engine.isRightWingUp());
  }

  @Test
  public void testArmsDownKeepsWingsDown() {
    TrackingResult result = new TrackingResult();
    result.isPlayerPresent = true;
    result.leftHandElevation = 0.02f;
    result.rightHandElevation = 0.02f;
    for (int i = 0; i < 8; i++) {
      engine.processTracking(result);
    }
    assertFalse(engine.isLeftWingUp());
    assertFalse(engine.isRightWingUp());
    assertEquals(ArmElevationMapper.WING_ANGLE_MIN, engine.getTargetLeftWingAngle());
    assertEquals(ArmElevationMapper.WING_ANGLE_MIN, engine.getTargetRightWingAngle());
  }

  @Test
  public void testProportionalWingAngleFromHandElevation() {
    TrackingResult result = new TrackingResult();
    result.isPlayerPresent = true;
    result.leftHandElevation = 0.5f;
    result.rightHandElevation = 0f;
    for (int i = 0; i < 12; i++) {
      engine.processTracking(result);
    }
    int leftAngle = engine.getTargetLeftWingAngle();
    assertTrue("Asa esquerda deve convergir perto de 45°", leftAngle >= 35 && leftAngle <= 55);
    assertEquals(ArmElevationMapper.WING_ANGLE_MIN, engine.getTargetRightWingAngle());
  }

  @Test
  public void wingAngleFollowsElevationOnSameFrame() {
    TrackingResult result = new TrackingResult();
    result.isPlayerPresent = true;
    result.leftHandElevation = 0.5f;
    result.rightHandElevation = 0.8f;
    engine.processTracking(result);
    assertEquals(45, engine.getTargetLeftWingAngle());
    assertEquals(72, engine.getTargetRightWingAngle());
    result.leftHandElevation = 0f;
    result.rightHandElevation = 0f;
    for (int i = 0; i < 2; i++) {
      engine.processTracking(result);
    }
    assertEquals(ArmElevationMapper.WING_ANGLE_MIN, engine.getTargetLeftWingAngle());
    assertEquals(ArmElevationMapper.WING_ANGLE_MIN, engine.getTargetRightWingAngle());
  }

  @Test
  public void testPlayerAtCenterProducesZeroOffsetWhenStandingStill() {
    TrackingResult result = new TrackingResult();
    result.isPlayerPresent = true;
    result.head.x = 0.50f;
    result.head.y = 0.30f;
    result.playerCentroidX = 0.50f;
    result.playerCentroidY = 0.50f;
    engine.processTracking(result);
    assertEquals("Yaw centro deve ser 0", 0, engine.getTargetHeadYaw());
    assertFalse("Não deve girar a base", engine.shouldRotateBase());
  }

  @Test
  public void testDeadzoneSuppressesJitterWhenStandingStill() {
    TrackingResult result = new TrackingResult();
    result.isPlayerPresent = true;
    float[] jitterX = {0.48f, 0.52f, 0.49f, 0.51f, 0.50f};
    for (int i = 0; i < jitterX.length; i++) {
      result.head.x = jitterX[i];
      engine.processTracking(result, 1000L * i);
      assertEquals("Deadzone X", 0, engine.getTargetHeadYaw());
    }
  }

  /**
   * Com o Astra na cabeça, girar a cabeça move o jogador na imagem. O yaw anda em passos, espera a
   * cabeça assentar entre eles, e para quando o jogador volta à zona central.
   */
  @Test
  public void testHeadYawStepsTowardPlayerAndWaitsForTheHeadToSettle() {
    TrackingResult result = new TrackingResult();
    result.isPlayerPresent = true;
    result.head.x = 0.20f;
    engine.processTracking(result, 0L);
    int firstStep = engine.getTargetHeadYaw();
    assertTrue("Yaw positivo para usuário à direita (x < 0.5)", firstStep > 5);

    engine.processTracking(result, 100L);
    assertEquals(
        "nada de novo passo antes de a cabeça assentar", firstStep, engine.getTargetHeadYaw());

    result.head.x = 0.45f;
    engine.processTracking(result, 1000L);
    assertEquals("jogador recentrado: cabeça fica onde está", firstStep, engine.getTargetHeadYaw());

    result.head.x = 0.80f;
    engine.processTracking(result, 2000L);
    engine.processTracking(result, 3000L);
    assertTrue("Yaw negativo para usuário à esquerda (x > 0.5)", engine.getTargetHeadYaw() < -5);
  }

  @Test
  public void testPoseFusionUsesAbsoluteHeadYawEvenWhenSeated() {
    TrackingResult result = new TrackingResult();
    result.isPlayerPresent = true;
    result.diagnostics.isPoseFusionActive = true;
    result.diagnostics.isSeatedPose = true;
    result.head.x = 0.35f;
    result.spine.x = 0.50f;
    result.head.y = 0.30f;
    engine.processTracking(result);
    assertTrue("Yaw segue posição absoluta da cabeça", engine.getTargetHeadYaw() > 3);
  }

  @Test
  public void testSeatedHeadTurnUsesRelativeYawWithoutPoseFusion() {
    TrackingResult result = new TrackingResult();
    result.isPlayerPresent = true;
    result.diagnostics.isSeatedPose = true;
    result.diagnostics.isPoseFusionActive = false;
    result.head.x = 0.38f;
    result.spine.x = 0.50f;
    result.head.y = 0.30f;
    engine.processTracking(result);
    assertTrue("Yaw relativo no modo sentado", engine.getTargetHeadYaw() > 3);
  }

  @Test
  public void testSeatedBodyShiftWithoutHeadTurnStaysNearCenter() {
    TrackingResult result = new TrackingResult();
    result.isPlayerPresent = true;
    result.diagnostics.isSeatedPose = true;
    result.diagnostics.isPoseFusionActive = false;
    result.head.x = 0.35f;
    result.spine.x = 0.35f;
    result.head.y = 0.30f;
    engine.processTracking(result);
    assertEquals("Corpo deslocado sem giro não gera yaw", 0, engine.getTargetHeadYaw());
  }

  @Test
  public void testBaseRotationIsAlwaysDisabled() {
    TrackingResult result = new TrackingResult();
    result.isPlayerPresent = true;
    result.playerCentroidX = 0.20f;
    result.head.x = 0.20f;
    engine.processTracking(result);
    assertFalse(engine.shouldRotateBase());
    assertEquals(0, engine.getBaseRotationAngle());
  }
}
