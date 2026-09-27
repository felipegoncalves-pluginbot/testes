package com.felipe.elftemplate.tracking3d;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Valida o pipeline métrico contra a geometria que foi realmente renderizada.
 *
 * <p>A diferença em relação ao golden dataset antigo é que aqui existe verdade de campo em metros: o
 * fixture sabe a estatura, a distância e a posição de cada punho, então o teste mede erro absoluto em
 * centímetros em vez de comparar frações de quadro com constantes escolhidas a dedo.
 */
public class MetricPipelineAccuracyTest {

  private static final float STATURE = 1.75f;

  @Test
  public void groundPlaneRecoversCameraMounting() {
    MetricPipelineFixture fixture = new MetricPipelineFixture();
    fixture.renderer.setCamera(1.05f, 12f);
    fixture.renderer.setBackWallDepthM(4.0f);
    fixture.renderer.getTracer().clear();
    fixture.scene.addPerson(STATURE, 2.0f, 0f, SyntheticHumanScene.Pose.ARMS_DOWN);
    fixture.run(7L);

    assertTrue("piso deve ser detectado", fixture.plane.isMeasured());
    assertEquals("altura da câmera em metros", 1.05f, fixture.plane.getCameraHeightM(), 0.05f);
    assertEquals("pitch em graus", 12f, fixture.plane.getPitchDeg(), 2.5f);
  }

  @Test
  public void statureIsRecoveredInMeters() {
    MetricPipelineFixture fixture = new MetricPipelineFixture();
    fixture.standardScene(STATURE, 2.5f, SyntheticHumanScene.Pose.ARMS_DOWN);
    fixture.run(11L);

    assertEquals("uma pessoa deve gerar um corpo", 1, fixture.clusterCount);
    assertEquals(
        "estatura medida em metros", STATURE, fixture.primaryCluster().topHeightM, 0.08f);
  }

  @Test
  public void personIsDetectedBeyondTheOldTwoMeterCeiling() {
    MetricPipelineFixture fixture = new MetricPipelineFixture();
    fixture.standardScene(STATURE, 3.4f, SyntheticHumanScene.Pose.ARMS_DOWN);
    fixture.run(13L);

    assertTrue("pessoa a 3,4 m deve ser detectada", fixture.clusterCount >= 1);
    assertEquals(
        "distância horizontal medida", 3.4f, fixture.primaryCluster().centroidZ, 0.25f);
  }

  @Test
  public void closePersonIsDetectedWithoutProximityHack() {
    MetricPipelineFixture fixture = new MetricPipelineFixture();
    fixture.standardScene(STATURE, 0.85f, SyntheticHumanScene.Pose.ARMS_DOWN);
    fixture.run(17L);

    assertTrue("pessoa a 0,85 m deve ser detectada", fixture.clusterCount >= 1);
    assertTrue("esqueleto deve ser ajustado de perto", fixture.skeletonFitted);
  }

  @Test
  public void headIsFoundNearTheTopOfTheBody() {
    MetricPipelineFixture fixture = new MetricPipelineFixture();
    fixture.standardScene(STATURE, 2.5f, SyntheticHumanScene.Pose.ARMS_DOWN);
    fixture.run(19L);

    assertTrue("esqueleto deve ser ajustado", fixture.skeletonFitted);
    float expectedHeadY = BodyProportions.HEAD_CENTER_HEIGHT * STATURE;
    assertEquals(
        "altura do centro da cabeça",
        expectedHeadY,
        fixture.skeleton.y(MetricSkeleton.HEAD),
        0.12f);
  }

  @Test
  public void tposeWristsLandNearTheRenderedWrists() {
    MetricPipelineFixture fixture = new MetricPipelineFixture();
    fixture.standardScene(STATURE, 2.5f, SyntheticHumanScene.Pose.T_POSE);
    fixture.run(23L);

    assertTrue("esqueleto deve ser ajustado", fixture.skeletonFitted);
    float truthY = fixture.scene.truthWristY(SyntheticHumanScene.Pose.T_POSE, true);
    assertEquals(
        "punho esquerdo na altura do ombro",
        truthY,
        fixture.skeleton.y(MetricSkeleton.LEFT_WRIST),
        0.15f);
    assertEquals(
        "punho direito na altura do ombro",
        truthY,
        fixture.skeleton.y(MetricSkeleton.RIGHT_WRIST),
        0.15f);
    assertTrue(
        "punho anatômico esquerdo fica em X maior",
        fixture.skeleton.x(MetricSkeleton.LEFT_WRIST)
            > fixture.skeleton.x(MetricSkeleton.RIGHT_WRIST));
  }

  @Test
  public void tposeArmSpanMatchesAnthropometry() {
    MetricPipelineFixture fixture = new MetricPipelineFixture();
    fixture.standardScene(STATURE, 2.5f, SyntheticHumanScene.Pose.T_POSE);
    fixture.run(29L);

    float span =
        fixture.skeleton.x(MetricSkeleton.LEFT_WRIST)
            - fixture.skeleton.x(MetricSkeleton.RIGHT_WRIST);
    float expected =
        2f * (BodyProportions.SHOULDER_HALF_SPAN + BodyProportions.ARM_LENGTH) * STATURE;
    assertEquals("envergadura punho a punho em metros", expected, span, 0.20f);
  }

  /** Mão erguida (~2,0 m do chão) só entra no quadro a partir de ~2,9 m: limite do FOV vertical. */
  private static final float RAISED_ARM_DISTANCE_M = 3.2f;

  @Test
  public void handsUpPutsBothWristsAboveTheShoulders() {
    MetricPipelineFixture fixture = new MetricPipelineFixture();
    fixture.standardScene(STATURE, RAISED_ARM_DISTANCE_M, SyntheticHumanScene.Pose.HANDS_UP);
    fixture.run(31L);

    assertTrue("esqueleto deve ser ajustado", fixture.skeletonFitted);
    assertTrue(
        raiseMessage(fixture, true), raisedBy(fixture, true) > 0.15f);
    assertTrue(
        raiseMessage(fixture, false), raisedBy(fixture, false) > 0.15f);
  }

  private static float raisedBy(MetricPipelineFixture fixture, boolean anatomicalLeft) {
    int wrist = anatomicalLeft ? MetricSkeleton.LEFT_WRIST : MetricSkeleton.RIGHT_WRIST;
    int shoulder = anatomicalLeft ? MetricSkeleton.LEFT_SHOULDER : MetricSkeleton.RIGHT_SHOULDER;
    return fixture.skeleton.y(wrist) - fixture.skeleton.y(shoulder);
  }

  /** Mensagem com os números medidos: uma falha precisa dizer o quanto errou, não só que errou. */
  private static String raiseMessage(MetricPipelineFixture fixture, boolean anatomicalLeft) {
    int wrist = anatomicalLeft ? MetricSkeleton.LEFT_WRIST : MetricSkeleton.RIGHT_WRIST;
    return String.format(
        "punho %s acima do ombro: delta=%.3f m wristY=%.3f conf=%.1f stature=%.3f",
        anatomicalLeft ? "esquerdo" : "direito",
        raisedBy(fixture, anatomicalLeft),
        fixture.skeleton.y(wrist),
        fixture.skeleton.confidence(wrist),
        fixture.skeleton.statureM);
  }

  @Test
  public void armsDownKeepsBothWristsBelowTheShoulders() {
    MetricPipelineFixture fixture = new MetricPipelineFixture();
    fixture.standardScene(STATURE, 2.5f, SyntheticHumanScene.Pose.ARMS_DOWN);
    fixture.run(37L);

    assertTrue(
        "punho esquerdo abaixo do ombro",
        fixture.skeleton.y(MetricSkeleton.LEFT_WRIST)
            < fixture.skeleton.y(MetricSkeleton.LEFT_SHOULDER));
    assertTrue(
        "punho direito abaixo do ombro",
        fixture.skeleton.y(MetricSkeleton.RIGHT_WRIST)
            < fixture.skeleton.y(MetricSkeleton.RIGHT_SHOULDER));
  }

  /**
   * Estabilidade temporal com a cena parada e apenas o ruído do sensor mudando.
   *
   * <p>Cada semente produz um padrão de ruído diferente, então isto mede a repetibilidade real do
   * estimador. Uma cabeça que oscila 10 cm entre frames idênticos inviabiliza qualquer jogo, e esse
   * tipo de instabilidade não aparece em teste de frame único.
   */
  @Test
  public void repeatedFramesKeepTheSkeletonStable() {
    float minHeadY = Float.MAX_VALUE;
    float maxHeadY = -Float.MAX_VALUE;
    float minStature = Float.MAX_VALUE;
    float maxStature = -Float.MAX_VALUE;
    for (int seed = 0; seed < 6; seed++) {
      MetricPipelineFixture fixture = new MetricPipelineFixture();
      fixture.standardScene(STATURE, 2.5f, SyntheticHumanScene.Pose.T_POSE);
      fixture.run(100L + seed);
      assertTrue("esqueleto deve ser ajustado na semente " + seed, fixture.skeletonFitted);
      float headY = fixture.skeleton.y(MetricSkeleton.HEAD);
      minHeadY = Math.min(minHeadY, headY);
      maxHeadY = Math.max(maxHeadY, headY);
      minStature = Math.min(minStature, fixture.skeleton.statureM);
      maxStature = Math.max(maxStature, fixture.skeleton.statureM);
    }
    assertTrue(
        "cabeça deve variar menos de 4 cm entre frames: " + (maxHeadY - minHeadY),
        (maxHeadY - minHeadY) < 0.04f);
    assertTrue(
        "estatura deve variar menos de 5 cm entre frames: " + (maxStature - minStature),
        (maxStature - minStature) < 0.05f);
  }

  @Test
  public void oneArmUpIsNotConfusedWithTheHead() {
    MetricPipelineFixture fixture = new MetricPipelineFixture();
    fixture.standardScene(STATURE, RAISED_ARM_DISTANCE_M, SyntheticHumanScene.Pose.LEFT_ARM_UP);
    fixture.run(41L);

    float headY = fixture.skeleton.y(MetricSkeleton.HEAD);
    float expectedHeadY = BodyProportions.HEAD_CENTER_HEIGHT * STATURE;
    assertEquals("cabeça não sobe para a mão erguida", expectedHeadY, headY, 0.14f);
    assertTrue(
        "punho esquerdo erguido",
        fixture.skeleton.y(MetricSkeleton.LEFT_WRIST)
            > fixture.skeleton.y(MetricSkeleton.LEFT_SHOULDER) + 0.15f);
    assertTrue(
        "punho direito continua baixo",
        fixture.skeleton.y(MetricSkeleton.RIGHT_WRIST)
            < fixture.skeleton.y(MetricSkeleton.RIGHT_SHOULDER));
  }
}
