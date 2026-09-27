package com.felipe.elftemplate.tracking3d;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Regressões da geometria real do Sanbot Elf: robô de 0,90 m, sensor baixo, jogador a 1,5–3 m.
 *
 * <p>Os testes antigos só renderizavam o jogador a 2,5 m com o Astra a 1,05 m, onde a cabeça cabe
 * no quadro e a pessoa é o primeiro corpo na varredura. Na distância de jogo a cabeça sai do
 * quadro, e o motor reprovava o jogador e ajustava o esqueleto num recorte da parede do fundo.
 */
public class RobotMountingTrackingTest {

  private static final float STATURE = 1.75f;

  @Test
  public void floorIsMeasuredWithTheSensorBelowTheRobotHeight() {
    MetricPipelineFixture fixture = new MetricPipelineFixture();
    fixture.renderer.setCamera(0.70f, 0f);
    fixture.renderer.setBackWallDepthM(3.8f);
    fixture.renderer.getTracer().clear();
    fixture.scene.addPerson(STATURE, 2.5f, 0f, SyntheticHumanScene.Pose.ARMS_DOWN);
    fixture.run(7L);

    assertTrue("piso a 0,70 m do sensor deve ser aceito", fixture.plane.isMeasured());
    assertEquals("altura do sensor", 0.70f, fixture.plane.getCameraHeightM(), 0.05f);
  }

  @Test
  public void playerWithHeadOutOfFrameIsTheTrackedBody() {
    MetricPipelineFixture fixture = new MetricPipelineFixture();
    fixture.standardScene(STATURE, 1.5f, SyntheticHumanScene.Pose.ARMS_DOWN);
    fixture.run(7L);

    assertTrue("jogador a 1,5 m deve ser aprovado", fixture.clusterCount >= 1);
    assertTrue("a 1,5 m a cabeça sai do quadro", fixture.primaryCluster().headOutOfFrame);
    assertEquals("corpo principal é o jogador", 1.5f, fixture.primaryCluster().centroidZ, 0.25f);
    assertTrue("esqueleto deve ser ajustado", fixture.skeletonFitted);
    assertEquals(
        "esqueleto no jogador, não na parede a 3,8 m",
        1.5f,
        fixture.skeleton.z(MetricSkeleton.SPINE),
        0.3f);
  }

  /**
   * Dois corpos de verdade: um adulto a 3,2 m, com a cabeça mais alta no quadro e portanto primeiro
   * na varredura, e uma criança a 1,8 m. O principal é o mais próximo, não o primeiro rótulo.
   */
  @Test
  public void closestBodyIsPrimaryEvenWhenAFartherBodyComesFirstInScanOrder() {
    MetricPipelineFixture fixture = new MetricPipelineFixture();
    fixture.standardScene(1.25f, 1.8f, SyntheticHumanScene.Pose.ARMS_DOWN);
    fixture.scene.addPerson(STATURE, 3.2f, -0.6f, SyntheticHumanScene.Pose.ARMS_DOWN);
    fixture.run(7L);

    assertEquals("criança e adulto aprovados", 2, fixture.clusterCount);
    assertEquals(
        "corpo principal é o mais próximo", 1.8f, fixture.primaryCluster().centroidZ, 0.3f);
    assertEquals("o outro corpo é o adulto", 3.2f, fixture.segmenter.getCluster(1).centroidZ, 0.3f);
  }

  /** Parede vista pelo vão entre as pernas: cercada pelo jogador, não é um segundo corpo. */
  @Test
  public void wallSeenBetweenTheLegsIsNotASecondBody() {
    MetricPipelineFixture fixture = new MetricPipelineFixture();
    fixture.standardScene(STATURE, 2.5f, SyntheticHumanScene.Pose.ARMS_DOWN);
    fixture.run(11L);

    assertEquals("só o jogador", 1, fixture.clusterCount);
  }

  /** Parede no limite de 4,2 m de alcance, fatiada pelo ruído: pedaços dela não são corpos. */
  @Test
  public void wallCutByTheRangeLimitIsNotABody() {
    MetricPipelineFixture fixture = new MetricPipelineFixture();
    fixture.standardScene(STATURE, 3.0f, SyntheticHumanScene.Pose.T_POSE);
    fixture.run(11L);

    assertEquals("só o jogador", 1, fixture.clusterCount);
    assertEquals("corpo é o jogador", 3.0f, fixture.primaryCluster().centroidZ, 0.3f);
  }

  /**
   * Um balcão de 1 m na frente esconde metade do corpo, mas a borda do jogador ainda dá para o
   * fundo.
   */
  @Test
  public void personBehindACounterIsStillTracked() {
    MetricPipelineFixture fixture = new MetricPipelineFixture();
    fixture.standardScene(STATURE, 2.0f, SyntheticHumanScene.Pose.ARMS_DOWN);
    for (float y = 0.10f; y <= 1.0f; y += 0.06f) {
      fixture.renderer.addWorldCapsule(-0.7f, y, 1.6f, 0.7f, y, 1.6f, 0.045f);
    }
    fixture.run(9L);

    assertTrue("jogador aprovado", fixture.clusterCount >= 1);
    assertEquals("corpo é o jogador", 2.0f, fixture.primaryCluster().centroidZ, 0.3f);
  }

  /**
   * Cabeça inclinada 10° para cima: o chão perto do jogador sai do quadro. A busca de pitch achava
   * um piso falso a ~30° para baixo, feito de uma fatia de parede e pessoa, e reprovava o jogador.
   */
  @Test
  public void tiltedUpSensorDoesNotInventASteepFloor() {
    MetricPipelineFixture fixture = new MetricPipelineFixture();
    fixture.renderer.setCamera(0.80f, -10f);
    fixture.renderer.setBackWallDepthM(3.8f);
    fixture.renderer.getTracer().clear();
    fixture.scene.addPerson(STATURE, 1.6f, 0f, SyntheticHumanScene.Pose.ARMS_DOWN);
    fixture.run(5L);

    if (fixture.plane.isMeasured()) {
      assertEquals("pitch medido", -10f, fixture.plane.getPitchDeg(), 6f);
    }
    assertTrue("jogador aprovado", fixture.clusterCount >= 1);
    assertEquals("corpo é o jogador", 1.6f, fixture.primaryCluster().centroidZ, 0.3f);
  }
}
