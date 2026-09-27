package com.felipe.elftemplate.tracking;

import static org.junit.Assert.*;

import com.felipe.elftemplate.tracking.PoseDepthFusion;

import org.junit.Before;
import org.junit.Test;

/**
 * Suíte de testes Golden Dataset / Evals de Visão Computacional.
 * Garante que mudanças no código de rastreamento nunca degradem a detecção de poses e gestos.
 */
public class GoldenDatasetTrackingTest {

  private KinectTrackingEngine trackingEngine;
  private int width;
  private int height;

  @Before
  public void setUp() {
    trackingEngine = new KinectTrackingEngine();
    width = SyntheticDepthFixtures.DEFAULT_WIDTH;
    height = SyntheticDepthFixtures.DEFAULT_HEIGHT;
  }

  @Test
  public void testStandingPersonDetectedWithAccurateCentroid() {
    short[] frame = SyntheticDepthFixtures.createStandingPerson(width, height, SyntheticDepthFixtures.PLAYER_BASE_DEPTH);

    TrackingResult result = trackingEngine.processDepthFrame(frame, width, height);

    assertNotNull("Resultado não deve ser nulo", result);
    assertTrue("Jogador em pé deve ser detectado", result.isPlayerPresent);
    assertEquals("Centróide X deve estar centralizado", 0.5f, result.playerCentroidX, 0.08f);
    assertEquals("Centróide Y deve estar centralizado", 0.5f, result.playerCentroidY, 0.08f);
    assertEquals("Distância Z deve ser ~1500mm", 1500, result.playerDistanceZ, 80);
    assertFalse("Braço esquerdo não deve estar levantado", result.isLeftHandRaised);
    assertFalse("Braço direito não deve estar levantado", result.isRightHandRaised);
  }

  @Test
  public void testTPoseDetectedReliably() {
    short[] frame = SyntheticDepthFixtures.createTPosePerson(width, height, SyntheticDepthFixtures.PLAYER_BASE_DEPTH);

    TrackingResult result = trackingEngine.processDepthFrame(frame, width, height);

    assertTrue("Jogador em T-Pose deve ser detectado", result.isPlayerPresent);
    assertEquals("Gesto detectado deve ser T_POSE", KinectTrackingEngine.GestureType.T_POSE, result.activeGesture);
  }

  @Test
  public void testHandsUpDetectedReliably() {
    short[] frame = SyntheticDepthFixtures.createHandsUpPerson(width, height, SyntheticDepthFixtures.PLAYER_BASE_DEPTH);

    TrackingResult result = trackingEngine.processDepthFrame(frame, width, height);

    assertTrue("Jogador deve ser detectado", result.isPlayerPresent);
    assertTrue("Mão esquerda deve estar levantada", result.isLeftHandRaised);
    assertTrue("Mão direita deve estar levantada", result.isRightHandRaised);
  }

  @Test
  public void testSensorNoiseResilience() {
    short[] frame = SyntheticDepthFixtures.createStandingPerson(width, height, SyntheticDepthFixtures.PLAYER_BASE_DEPTH);
    // Injeta 15% de ruído típico de sensor Orbbec Astra
    SyntheticDepthFixtures.injectSensorNoise(frame, 0.15f, 42L);

    TrackingResult result = trackingEngine.processDepthFrame(frame, width, height);

    assertTrue("Jogador deve continuar sendo detectado mesmo com 15% de ruído", result.isPlayerPresent);
    assertEquals("Centróide X não pode desviar bruscamente com ruído", 0.5f, result.playerCentroidX, 0.12f);
  }

  @Test
  public void testDuckingDetectionUnderBaselineCalibration() {
    short[] standingFrame = SyntheticDepthFixtures.createStandingPerson(width, height, SyntheticDepthFixtures.PLAYER_BASE_DEPTH);
    // Calibra a linha de base em pé
    for (int i = 0; i < 5; i++) {
      trackingEngine.processDepthFrame(standingFrame, width, height);
    }

    short[] duckingFrame = SyntheticDepthFixtures.createDuckingPerson(width, height, SyntheticDepthFixtures.PLAYER_BASE_DEPTH);
    TrackingResult duckResult = trackingEngine.processDepthFrame(duckingFrame, width, height);

    assertTrue("Jogador deve ser detectado agachado", duckResult.isPlayerPresent);
    assertTrue("Centróide Y deve descer em relação ao centro", duckResult.playerCentroidY > 0.52f);
  }

  @Test
  public void testExecutionLatencyWithinBudget() {
    short[] frame = SyntheticDepthFixtures.createStandingPerson(width, height, SyntheticDepthFixtures.PLAYER_BASE_DEPTH);

    // Warm up JIT
    for (int i = 0; i < 50; i++) {
      trackingEngine.processDepthFrame(frame, width, height);
    }

    long start = System.nanoTime();
    int iterations = 100;
    for (int i = 0; i < iterations; i++) {
      trackingEngine.processDepthFrame(frame, width, height);
    }
    long totalElapsedUs = (System.nanoTime() - start) / 1000;
    long avgLatencyUs = totalElapsedUs / iterations;

    // Latência média deve ser inferior a 3000 microssegundos (3ms) por frame no desktop
    assertTrue("Latência do algoritmo (" + avgLatencyUs + " us) excede o orçamento de 3ms", avgLatencyUs < 3000);
  }

  @Test
  public void closeSeatedPerson_nearMode_armsNotRaised() {
    short[] frame =
        SyntheticDepthFixtures.createSeatedPerson(width, height, (short) 720);
    TrackingResult r = trackingEngine.processDepthFrame(frame, width, height);
    assertTrue("Pessoa próxima sentada deve ser detectada", r.isPlayerPresent);
    assertTrue("Z=720 mm deve ligar proximidade", r.diagnostics.isNearProximityMode);
    assertTrue("ombro esquerdo à esquerda do direito", r.leftShoulder.x < r.rightShoulder.x);
    assertTrue("mão esquerda não pode ir a X=0", r.leftHand.x > 0.05f);
    assertTrue("mão direita não pode ir a X=1", r.rightHand.x < 0.95f);
    assertTrue("mão abaixo da cabeça (y maior)", r.leftHand.y > r.head.y);
    assertTrue("mão dir. abaixo da cabeça", r.rightHand.y > r.head.y);
    assertFalse("braço esq. abaixado no colo", r.isLeftHandRaised);
    assertFalse("braço dir. abaixado no colo", r.isRightHandRaised);
    assertTrue(r.leftHandElevation < 0.35f);
    assertTrue(r.rightHandElevation < 0.35f);
  }

  @Test
  public void closeSeatedWithChair_notHandsUp_handsBelowHead() {
    short[] frame =
        SyntheticDepthFixtures.createCloseSeatedPersonWithChair(width, height, (short) 720);
    TrackingResult r = trackingEngine.processDepthFrame(frame, width, height);
    assertTrue(r.isPlayerPresent);
    assertTrue(r.diagnostics.isNearProximityMode);
    assertFalse(
        "encosto da cadeira não é HANDS_UP (viewer: mãos y < cabeça)",
        r.isLeftHandRaised && r.isRightHandRaised);
    assertTrue("mão esq. não pode ficar acima da cabeça", r.leftHand.y > r.head.y - 0.02f);
    assertTrue("mão dir. não pode ficar acima da cabeça", r.rightHand.y > r.head.y - 0.02f);
    assertTrue(r.leftHandElevation < ArmElevationTracker.RAISE_ENTER_ELEV + 0.05f);
    assertTrue(r.rightHandElevation < ArmElevationTracker.RAISE_ENTER_ELEV + 0.05f);
  }

  @Test
  public void closePersonWithOverheadLamp_handNotAtCeiling() {
    short[] frame =
        SyntheticDepthFixtures.createClosePersonWithOverheadLamp(width, height, (short) 720);
    TrackingResult r = trackingEngine.processDepthFrame(frame, width, height);
    assertTrue(r.isPlayerPresent);
    assertTrue(r.diagnostics.isNearProximityMode);
    assertTrue(
        "lâmpada no teto não é punho y=" + r.rightHand.y + " head=" + r.head.y,
        r.rightHand.y > r.head.y - 0.05f);
    assertTrue("asa não vai a 90° por lâmpada elev=" + r.rightHandElevation, r.rightHandElevation < 0.85f);
  }

  @Test
  public void rgbFusionIsOffInProximity() {
    TrackingResult near = new TrackingResult();
    near.isPlayerPresent = true;
    near.diagnostics.isNearProximityMode = true;
    assertFalse(PoseDepthFusion.allowRgbFusion(near));
    TrackingResult far = new TrackingResult();
    far.isPlayerPresent = true;
    far.diagnostics.isNearProximityMode = false;
    assertTrue(PoseDepthFusion.allowRgbFusion(far));
    assertFalse(PoseDepthFusion.allowRgbFusion(null));
  }
}
