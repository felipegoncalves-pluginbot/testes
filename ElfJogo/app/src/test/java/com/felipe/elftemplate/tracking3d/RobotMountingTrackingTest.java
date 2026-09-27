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

  @Test
  public void closestBodyIsPrimaryWhenTheWallComesFirstInScanOrder() {
    MetricPipelineFixture fixture = new MetricPipelineFixture();
    fixture.standardScene(STATURE, 3.0f, SyntheticHumanScene.Pose.ARMS_DOWN);
    fixture.run(7L);

    assertTrue("cena tem jogador e recortes de parede", fixture.clusterCount >= 2);
    assertEquals("corpo principal é o jogador", 3.0f, fixture.primaryCluster().centroidZ, 0.3f);
    for (int slot = 1; slot < fixture.clusterCount; slot++) {
      assertTrue(
          "slot 0 é o mais próximo",
          fixture.segmenter.getCluster(slot).centroidZ >= fixture.primaryCluster().centroidZ);
    }
  }
}
