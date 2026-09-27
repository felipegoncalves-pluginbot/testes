package com.felipe.elftemplate.tracking;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

/** Regressão: posição de mãos no esqueleto depth-blob (sem ML Kit fusion). */
public class DepthBlobHandDetectionTest {

  private KinectTrackingEngine engine;
  private int width;
  private int height;

  @Before
  public void setUp() {
    engine = new KinectTrackingEngine();
    width = SyntheticDepthFixtures.DEFAULT_WIDTH;
    height = SyntheticDepthFixtures.DEFAULT_HEIGHT;
  }

  @Test
  public void handsUp_handsAboveShoulders_notAtTorsoFallback() {
    short[] frame =
        SyntheticDepthFixtures.createHandsUpPerson(
            width, height, SyntheticDepthFixtures.PLAYER_BASE_DEPTH);
    TrackingResult r = engine.processDepthFrame(frame, width, height);

    assertTrue(r.isPlayerPresent);
    assertTrue("Mão esquerda levantada", r.isLeftHandRaised);
    assertTrue("Mão direita levantada", r.isRightHandRaised);
  }

  @Test
  public void handsUp_handY_mustBeAboveShoulder_notCentroidHip() {
    short[] frame =
        SyntheticDepthFixtures.createHandsUpPerson(
            width, height, SyntheticDepthFixtures.PLAYER_BASE_DEPTH);
    TrackingResult r = engine.processDepthFrame(frame, width, height);

    float shoulderY = r.leftShoulder.y;
    float hipFallbackY = r.playerCentroidY + 0.18f;

    assertTrue(
        "Mão esq. deve estar acima do ombro (y menor), got handY="
            + r.leftHand.y
            + " shoulderY="
            + shoulderY,
        r.leftHand.y < shoulderY - 0.05f);
    assertTrue(
        "Mão esq. não deve usar fallback torso+0.18 (hip), got handY="
            + r.leftHand.y
            + " hipFallback="
            + hipFallbackY,
        Math.abs(r.leftHand.y - hipFallbackY) > 0.08f);
    assertTrue(
        "Mão dir. deve estar acima do ombro",
        r.rightHand.y < r.rightShoulder.y - 0.05f);
  }

  @Test
  public void closerFurnitureBlob_doesNotStealPlayer() {
    short[] frame =
        SyntheticDepthFixtures.createStandingPerson(
            width, height, SyntheticDepthFixtures.PLAYER_BASE_DEPTH);
    SyntheticDepthFixtures.addSideBlob(frame, width, height, 8, height / 2, 4, (short) 900);
    TrackingResult r = engine.processDepthFrame(frame, width, height);
    assertTrue(r.isPlayerPresent);
    assertTrue(
        "jogador deve continuar no centro, não no blob da cadeira cx=" + r.playerCentroidX,
        r.playerCentroidX > 0.35f && r.playerCentroidX < 0.65f);
  }

  @Test
  public void seatedPerson_isDetected_handsNotAtBottom() {
    short[] frame =
        SyntheticDepthFixtures.createSeatedPerson(
            width, height, SyntheticDepthFixtures.PLAYER_BASE_DEPTH);
    TrackingResult r = engine.processDepthFrame(frame, width, height);
    assertTrue("Pessoa sentada deve ser detectada", r.isPlayerPresent);
    assertTrue("Mão sentada não pode ir ao fundo do quadro y=" + r.leftHand.y, r.leftHand.y < 0.85f);
  }

  @Test
  public void standing_handsMustNotSnapToFeet() {
    short[] frame =
        SyntheticDepthFixtures.createStandingPerson(
            width, height, SyntheticDepthFixtures.PLAYER_BASE_DEPTH);
    TrackingResult r = engine.processDepthFrame(frame, width, height);

    assertTrue(r.isPlayerPresent);
    assertTrue(
        "Mão esq. não pode ir ao pé (logcat Elf: handY≈0.90). handY="
            + r.leftHand.y
            + " hipY="
            + r.leftHip.y,
        r.leftHand.y <= r.leftHip.y + 0.05f);
    assertTrue(
        "Mão dir. não pode ir ao pé. handY=" + r.rightHand.y + " hipY=" + r.rightHip.y,
        r.rightHand.y <= r.rightHip.y + 0.05f);
    assertTrue("Mão esq. abaixo do meio da tela é perna, não braço", r.leftHand.y < 0.80f);
  }

  @Test
  public void tPose_handsLateral_notCollapsedToTorso() {
    short[] frame =
        SyntheticDepthFixtures.createTPosePerson(
            width, height, SyntheticDepthFixtures.PLAYER_BASE_DEPTH);
    TrackingResult r = engine.processDepthFrame(frame, width, height);

    assertTrue(r.isPlayerPresent);
    assertTrue(
        "Mão esq. lateral em T-Pose",
        r.leftHand.x < r.playerCentroidX - 0.15f);
    assertTrue(
        "Mão dir. lateral em T-Pose",
        r.rightHand.x > r.playerCentroidX + 0.15f);
  }

  @Test
  public void handsUp_at640x480_withNoise_handsStillAboveShoulders() {
    int w = 640;
    int h = 480;
    short[] small =
        SyntheticDepthFixtures.createHandsUpPerson(
            SyntheticDepthFixtures.DEFAULT_WIDTH,
            SyntheticDepthFixtures.DEFAULT_HEIGHT,
            SyntheticDepthFixtures.PLAYER_BASE_DEPTH);
    short[] frame = SyntheticDepthFixtures.upscaleNearest(
        small,
        SyntheticDepthFixtures.DEFAULT_WIDTH,
        SyntheticDepthFixtures.DEFAULT_HEIGHT,
        w,
        h);
    SyntheticDepthFixtures.injectSensorNoise(frame, 0.12f, 99L);
  // Objeto lateral (cadeira) — ruído comum no cenário real
    SyntheticDepthFixtures.addSideBlob(frame, w, h, w * 3 / 4, h / 2, 35, (short) 1400);

    TrackingResult r = engine.processDepthFrame(frame, w, h);

    assertTrue(r.isPlayerPresent);
    assertTrue(
        "Com ruído 640x480 mão esq. acima do ombro",
        r.leftHand.y < r.leftShoulder.y - 0.03f);
    assertTrue(
        "Com ruído 640x480 mão dir. acima do ombro",
        r.rightHand.y < r.rightShoulder.y - 0.03f);
    assertFalse(
        "Fallback antigo centroid+0.18 esq.",
        Math.abs(r.leftHand.y - (r.playerCentroidY + 0.18f)) < 0.05f);
  }

  @Test
  public void standing_armsDown_handsNotRaised() {
    short[] frame =
        SyntheticDepthFixtures.createStandingPerson(
            width, height, SyntheticDepthFixtures.PLAYER_BASE_DEPTH);
    TrackingResult r = engine.processDepthFrame(frame, width, height);

    assertTrue(r.isPlayerPresent);
    assertTrue(
        "Mão esq. abaixo do ombro com braço ao lado",
        r.leftHand.y > r.leftShoulder.y + 0.02f);
    assertFalse(
        "Braço abaixado não deve marcar levantada esq.",
        r.isLeftHandRaised);
    assertFalse(
        "Braço abaixado não deve marcar levantada dir.",
        r.isRightHandRaised);
    assertTrue(
        "Elevação esq. baixa com braço abaixado",
        r.leftHandElevation < ArmElevationTracker.RAISE_ENTER_ELEV);
  }

  @Test
  public void standing_at640x480_armsAlongBody_mayUseLowPixelCount() {
    int w = 640;
    int h = 480;
    short[] small =
        SyntheticDepthFixtures.createStandingPerson(
            SyntheticDepthFixtures.DEFAULT_WIDTH,
            SyntheticDepthFixtures.DEFAULT_HEIGHT,
            SyntheticDepthFixtures.PLAYER_BASE_DEPTH);
    short[] frame = SyntheticDepthFixtures.upscaleNearest(
        small,
        SyntheticDepthFixtures.DEFAULT_WIDTH,
        SyntheticDepthFixtures.DEFAULT_HEIGHT,
        w,
        h);
    SyntheticDepthFixtures.injectSensorNoise(frame, 0.15f, 77L);

    TrackingResult r = engine.processDepthFrame(frame, w, h);
    assertTrue(r.isPlayerPresent);
    // Braços colados ao tronco em cena real → leftCount pode ficar < 4 e ativar fallback torso.
    if (r.diagnostics.leftHandPixelCount < 3) {
      assertTrue(
          "Fallback deve ancorar no ombro, não no quadril",
          r.leftHand.y > r.leftShoulder.y
              && r.leftHand.y < r.leftShoulder.y + 0.20f);
    }
  }

  @Test
  public void standing_withTableSideBlob_armsDown_notRaised() {
    int w = 640;
    int h = 480;
    short[] small =
        SyntheticDepthFixtures.createStandingPerson(
            SyntheticDepthFixtures.DEFAULT_WIDTH,
            SyntheticDepthFixtures.DEFAULT_HEIGHT,
            SyntheticDepthFixtures.PLAYER_BASE_DEPTH);
    short[] frame =
        SyntheticDepthFixtures.upscaleNearest(
            small,
            SyntheticDepthFixtures.DEFAULT_WIDTH,
            SyntheticDepthFixtures.DEFAULT_HEIGHT,
            w,
            h);
    SyntheticDepthFixtures.addSideBlob(frame, w, h, w * 3 / 4, h / 3, 28, (short) 1100);
    SyntheticDepthFixtures.injectSensorNoise(frame, 0.12f, 42L);

    TrackingResult r = engine.processDepthFrame(frame, w, h);
    assertTrue(r.isPlayerPresent);
    assertFalse("Objeto lateral não pode levantar braço esq.", r.isLeftHandRaised);
    assertFalse("Objeto lateral não pode levantar braço dir.", r.isRightHandRaised);
    assertTrue(r.leftHandElevation < ArmElevationTracker.RAISE_ENTER_ELEV);
    assertTrue(r.rightHandElevation < ArmElevationTracker.RAISE_ENTER_ELEV);
  }

  @Test
  public void extremeLateralNoise_isClampedWithinAnthropometricEnvelope() {
    int w = 640;
    int h = 480;
    short[] small =
        SyntheticDepthFixtures.createStandingPerson(
            SyntheticDepthFixtures.DEFAULT_WIDTH,
            SyntheticDepthFixtures.DEFAULT_HEIGHT,
            SyntheticDepthFixtures.PLAYER_BASE_DEPTH);
    short[] frame =
        SyntheticDepthFixtures.upscaleNearest(
            small,
            SyntheticDepthFixtures.DEFAULT_WIDTH,
            SyntheticDepthFixtures.DEFAULT_HEIGHT,
            w,
            h);
    // Adiciona objeto/ruído no extremo direito (x = 600 / 640 = 0.93)
    SyntheticDepthFixtures.addSideBlob(frame, w, h, 600, h / 2, 25, (short) 1500);

    TrackingResult r = engine.processDepthFrame(frame, w, h);
    assertTrue(r.isPlayerPresent);
    assertTrue(
        "Mão direita deve sofrer clamp e não teleportar para o ruído extremo em x=0.93",
        r.rightHand.x <= r.rightShoulder.x + 0.35f);
  }
}
