package com.felipe.elftemplate.tracking3d;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

/**
 * Acurácia do classificador de partes embarcado, medida em centímetros contra a pose renderizada.
 *
 * <p>Todas as poses aqui são configurações específicas que não estavam no treino — o treino usou
 * ângulos sorteados de faixas contínuas. Então o que estes testes medem é generalização dentro da
 * distribuição treinada, que é exatamente a propriedade que faz o classificador valer a pena.
 *
 * <p>As tolerâncias não são arbitrárias: a grade decimada tem célula de ~2,6 cm a 3 m, o plano do chão
 * é estimado e não dado, e o classificador vê só a superfície. Um erro de poucos centímetros é o piso
 * físico do arranjo, não folga de teste.
 */
public class BodyPartPoseAccuracyTest {

  private static final float STATURE = 1.75f;

  /** Distância onde um adulto com braço erguido ainda cabe no campo de visão vertical. */
  private static final float RAISED_ARM_DISTANCE_M = 3.2f;

  private LearnedPipelineFixture fixture;

  @Before
  public void loadModel() throws IOException {
    fixture = new LearnedPipelineFixture();
    Assume.assumeTrue(
        "modelo de partes ausente; gere com -Pbodypart.train=true", fixture.loadShippedModel());
  }

  @Test
  public void headIsProposedNearTheRenderedHead() {
    fixture.standardCamera(2.5f);
    fixture.poseArms(STATURE, -85f, 0f, 0f);
    fixture.run(2.5f, 4242L);

    assertTrue("esqueleto deve ser ajustado", fixture.skeletonFitted);
    assertTrue(
        "classificador deve rotular celulas: " + fixture.labeledCells, fixture.labeledCells > 100);
    assertTrue(
        fixture.describe(MetricSkeleton.HEAD, "cabeca"),
        fixture.proposalErrorM(MetricSkeleton.HEAD) < 0.14f);
  }

  @Test
  public void tposeWristsAreProposedNearTheRenderedWrists() {
    fixture.standardCamera(2.6f);
    fixture.poseArms(STATURE, 0f, 0f, 0f);
    fixture.run(2.6f, 5353L);

    assertTrue("esqueleto deve ser ajustado", fixture.skeletonFitted);
    assertTrue(
        fixture.describe(MetricSkeleton.LEFT_WRIST, "punho esquerdo T-pose"),
        fixture.proposalErrorM(MetricSkeleton.LEFT_WRIST) < 0.16f);
    assertTrue(
        fixture.describe(MetricSkeleton.RIGHT_WRIST, "punho direito T-pose"),
        fixture.proposalErrorM(MetricSkeleton.RIGHT_WRIST) < 0.16f);
  }

  @Test
  public void raisedArmDoesNotCollapseIntoTheHead() {
    fixture.standardCamera(RAISED_ARM_DISTANCE_M);
    fixture.poseLeftArmOnly(STATURE, 88f, -85f);
    fixture.run(RAISED_ARM_DISTANCE_M, 6464L);

    assertTrue("esqueleto deve ser ajustado", fixture.skeletonFitted);
    assertTrue(
        fixture.describe(MetricSkeleton.HEAD, "cabeca com braco erguido"),
        fixture.proposalErrorM(MetricSkeleton.HEAD) < 0.16f);
    float wristAboveHead =
        fixture.proposer.y(MetricSkeleton.LEFT_WRIST) - fixture.proposer.y(MetricSkeleton.HEAD);
    assertTrue(
        "punho erguido deve ficar acima da cabeca, delta " + wristAboveHead, wristAboveHead > 0.05f);
  }

  /**
   * Braço aberto para frente e para o lado: o caso em que a geometria não tem extremo e o
   * classificador tem.
   *
   * <p>Nesta pose a mão sai do plano frontal mas continua com silhueta própria. O caminho geodésico
   * costuma perder o punho aqui porque o percurso pela superfície não termina numa ponta clara; o
   * classificador decide pixel por pixel e não depende disso.
   */
  @Test
  public void diagonalForwardReachIsFoundWhereGeometryHasNoExtremum() {
    fixture.standardCamera(2.4f);
    fixture.poseArms(STATURE, -6f, 48f, 15f);
    fixture.run(2.4f, 7575L);

    assertTrue("esqueleto deve ser ajustado", fixture.skeletonFitted);
    boolean leftFound = fixture.proposer.hasProposal(MetricSkeleton.LEFT_WRIST);
    boolean rightFound = fixture.proposer.hasProposal(MetricSkeleton.RIGHT_WRIST);
    assertTrue(
        "nenhum punho diagonal proposto. " + fixture.describePartCounts(), leftFound || rightFound);
    int joint = leftFound ? MetricSkeleton.LEFT_WRIST : MetricSkeleton.RIGHT_WRIST;
    assertTrue(fixture.describe(joint, "punho diagonal"), fixture.proposalErrorM(joint) < 0.20f);
  }

  /**
   * Braço apontado direto para o sensor: o classificador tem de desistir, não inventar.
   *
   * <p>Um membro alinhado com o raio de visão é quase sem informação em profundidade — a mão oclui o
   * antebraço, que oclui o braço, e a superfície visível é um disco que localmente parece tronco.
   * Medido neste arranjo, o classificador coloca zero célula em mão e antebraço. Isso é uma limitação
   * física do sensor único, não um defeito do modelo, e o Kinect real degrada no mesmo caso.
   *
   * <p>O que se exige aqui é o comportamento correto diante da ambiguidade: nenhuma proposta com
   * convicção, para a junta ficar marcada como não medida e o consumidor decidir. Uma mão inventada com
   * confiança alta seria muito pior que mão ausente, porque acionaria a asa do robô sem motivo.
   */
  @Test
  public void armPointedAtTheSensorDoesNotHallucinateAHand() {
    fixture.standardCamera(2.4f);
    fixture.poseArms(STATURE, -8f, 78f, 12f);
    fixture.run(2.4f, 7575L);

    assertTrue("esqueleto deve ser ajustado", fixture.skeletonFitted);
    assertTrue("tronco e pernas devem continuar rotulados", fixture.labeledCells > 500);
    assertNoConfidentButWrongProposal(MetricSkeleton.LEFT_WRIST, "punho esquerdo");
    assertNoConfidentButWrongProposal(MetricSkeleton.RIGHT_WRIST, "punho direito");
  }

  /** Ou a proposta não existe, ou ela está de fato perto da verdade. Nunca confiante e errada. */
  private void assertNoConfidentButWrongProposal(int joint, String name) {
    if (!fixture.proposer.hasProposal(joint)) {
      return;
    }
    assertTrue(
        name + " proposto com conviccao mas errado: " + fixture.describe(joint, name),
        fixture.proposalErrorM(joint) < 0.25f);
  }

  @Test
  public void handsAreProposedAtCloseRange() {
    fixture.standardCamera(1.3f);
    fixture.poseArms(STATURE, -20f, 30f, 60f);
    fixture.run(1.3f, 8686L);

    assertTrue("esqueleto deve ser ajustado a 1,3 m", fixture.skeletonFitted);
    assertTrue(
        "de perto sobram muitas celulas de corpo: " + fixture.labeledCells,
        fixture.labeledCells > 300);
    assertTrue(
        "de perto o classificador deve propor varias juntas: " + fixture.proposalCount,
        fixture.proposalCount >= 6);
  }

  /**
   * Estabilidade do caminho de produção com a cena parada e só o ruído do sensor mudando.
   *
   * <p>É o que decide se o esqueleto treme na tela. O que se mede aqui é a saída depois do filtro
   * temporal, porque é ela que vai para o overlay e para os motores. O estágio cru oscila mais, e
   * deliberadamente: um estimador por quadro sem memória é a escolha de arquitetura do Kinect, e o
   * filtro é a metade seguinte dessa escolha.
   */
  @Test
  public void productionPathKeepsTheWristStableAcrossFrames() {
    fixture.poseArms(STATURE, 0f, 0f, 0f);
    float minX = Float.MAX_VALUE;
    float maxX = -Float.MAX_VALUE;
    float minY = Float.MAX_VALUE;
    float maxY = -Float.MAX_VALUE;
    int frames = 0;
    for (int frame = 0; frame < 6; frame++) {
      fixture.runProductionFrame(2.6f, 3000L + frame, frame);
      if (!fixture.skeletonFitted || frame < 2) {
        continue;
      }
      frames++;
      float x = fixture.skeleton.x(MetricSkeleton.LEFT_WRIST);
      float y = fixture.skeleton.y(MetricSkeleton.LEFT_WRIST);
      minX = Math.min(minX, x);
      maxX = Math.max(maxX, x);
      minY = Math.min(minY, y);
      maxY = Math.max(maxY, y);
    }
    assertTrue("deve haver quadros medidos: " + frames, frames >= 3);
    assertTrue(
        String.format("punho filtrado variou %.1f cm em X", (maxX - minX) * 100f),
        (maxX - minX) < 0.05f);
    assertTrue(
        String.format("punho filtrado variou %.1f cm em Y", (maxY - minY) * 100f),
        (maxY - minY) < 0.05f);
  }

  /**
   * Um braço que o classificador enxerga tem de sair do esqueleto como medido, não sintetizado.
   *
   * <p>É essa distinção que o overlay e as asas do robô consultam para decidir se reagem, então não
   * basta a proposta estar certa: ela precisa sobreviver ao refinador e chegar ao esqueleto.
   */
  @Test
  public void confidentProposalsSurviveIntoTheSkeleton() {
    fixture.standardCamera(2.6f);
    fixture.poseArms(STATURE, 0f, 0f, 0f);
    fixture.run(2.6f, 9797L);

    int overridden = fixture.refineSkeletonWithLearnedModel();
    assertTrue("refinador deve assumir varias juntas: " + overridden, overridden >= 4);
    float wristError = fixture.skeletonErrorM(MetricSkeleton.LEFT_WRIST);
    assertTrue(
        "punho esquerdo refinado a " + (wristError * 100f) + " cm da verdade", wristError < 0.18f);
    assertTrue(
        "punho refinado deve ser marcado como medido, confianca "
            + fixture.skeleton.confidence(MetricSkeleton.LEFT_WRIST),
        fixture.skeleton.confidence(MetricSkeleton.LEFT_WRIST)
            >= GeodesicSkeletonFitter.CONFIDENCE_PATH);
  }
}
