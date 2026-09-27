package com.felipe.elftemplate.tracking3d;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.felipe.elftemplate.tracking.TrackingResult;
import org.junit.Test;

/**
 * Garante que o esqueleto métrico chega correto ao contrato consumido pelos jogos.
 *
 * <p>É a fronteira mais perigosa do sistema: é onde lateralidade e normalização acontecem. Um erro
 * aqui inverte asas e controles sem quebrar nenhum outro teste, que foi exatamente o histórico do
 * projeto.
 */
public class MetricToLegacyContractTest {

  private static final float STATURE = 1.75f;

  private TrackingResult runAdapter(SyntheticHumanScene.Pose pose, float distanceM) {
    MetricPipelineFixture fixture = new MetricPipelineFixture();
    fixture.standardScene(STATURE, distanceM, pose);
    fixture.run(53L);
    TrackingResult legacy = new TrackingResult();
    TrackingResultAdapter.apply(
        fixture.skeleton, fixture.plane, fixture.cloud.getIntrinsics(), legacy);
    return legacy;
  }

  @Test
  public void jointsLandInsideTheNormalizedFrame() {
    TrackingResult legacy = runAdapter(SyntheticHumanScene.Pose.ARMS_DOWN, 2.5f);

    assertTrue("jogador presente no contrato antigo", legacy.isPlayerPresent);
    assertTrue("cabeça dentro do quadro em X", legacy.head.x >= 0f && legacy.head.x <= 1f);
    assertTrue("cabeça dentro do quadro em Y", legacy.head.y >= 0f && legacy.head.y <= 1f);
    assertTrue("cabeça acima do quadril na tela", legacy.head.y < legacy.leftHip.y);
    assertEquals("distância do tronco em mm", 2500, legacy.playerDistanceZ, 260);
  }

  @Test
  public void headProjectsNearTheOpticalAxisWhenCentered() {
    TrackingResult legacy = runAdapter(SyntheticHumanScene.Pose.ARMS_DOWN, 2.5f);

    assertEquals("pessoa centrada projeta a cabeça no meio do quadro", 0.5f, legacy.head.x, 0.06f);
  }

  /**
   * O punho anatômico esquerdo tem que sair no lado <em>direito</em> da imagem, porque a pessoa está
   * de frente. Se essa relação inverter, as asas do robô espelham errado.
   */
  @Test
  public void anatomicalSidesMapToOppositeImageSides() {
    TrackingResult legacy = runAdapter(SyntheticHumanScene.Pose.T_POSE, 2.5f);

    assertTrue(
        "ombro de tela esquerdo tem X menor que o direito",
        legacy.leftShoulder.x < legacy.rightShoulder.x);
    assertTrue(
        "mão de tela esquerda tem X menor que a direita", legacy.leftHand.x < legacy.rightHand.x);
    assertTrue("mão de tela esquerda no lado esquerdo do quadro", legacy.leftHand.x < 0.5f);
    assertTrue("mão de tela direita no lado direito do quadro", legacy.rightHand.x > 0.5f);
  }

  /**
   * No pipeline antigo toda junta recebia {@code playerDistanceZ}, a distância média do corpo. A
   * propriedade que precisa valer agora é que cada junta carrega a própria profundidade.
   */
  @Test
  public void perJointDepthIsIndependent() {
    TrackingResult legacy = runAdapter(SyntheticHumanScene.Pose.T_POSE, 2.5f);

    assertTrue("cabeça tem profundidade própria em mm", legacy.head.z > 1500);
    int handToHead = Math.abs(legacy.leftHand.z - legacy.head.z);
    int hipToHead = Math.abs(legacy.leftHip.z - legacy.head.z);
    assertTrue(
        "mão e cabeça não podem compartilhar a mesma profundidade: delta=" + handToHead + "mm",
        handToHead > 15);
    assertTrue(
        "quadril e cabeça não podem compartilhar a mesma profundidade: delta=" + hipToHead + "mm",
        hipToHead > 15);
  }

  @Test
  public void gesturesReachTheLegacyContractWithSwappedSides() {
    MetricPipelineFixture fixture = new MetricPipelineFixture();
    fixture.standardScene(STATURE, 3.2f, SyntheticHumanScene.Pose.LEFT_ARM_UP);
    fixture.run(59L);
    MetricGestureEngine gestureEngine = new MetricGestureEngine();
    gestureEngine.update(fixture.skeleton);
    TrackingResult legacy = new TrackingResult();
    TrackingResultAdapter.apply(
        fixture.skeleton, fixture.plane, fixture.cloud.getIntrinsics(), legacy);
    gestureEngine.applyTo(legacy);

    assertTrue("braço anatômico esquerdo detectado erguido", gestureEngine.isAnatomicalLeftRaised());
    assertTrue(
        "no contrato antigo isso é a mão do lado direito da imagem", legacy.isRightHandRaised);
    assertTrue("elevação do lado direito da imagem alta", legacy.rightHandElevation > 0.75f);
    assertTrue("elevação do lado esquerdo da imagem baixa", legacy.leftHandElevation < 0.35f);
  }
}
