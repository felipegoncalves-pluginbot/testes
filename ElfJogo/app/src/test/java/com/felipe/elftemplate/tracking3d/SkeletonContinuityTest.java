package com.felipe.elftemplate.tracking3d;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.felipe.elftemplate.tracking.TrackingResult;
import org.junit.Test;

/**
 * Regressão da oscilação de asa observada no robô.
 *
 * <p>No logcat do Modo Espelho a elevação do braço alternava entre 0,00 e 1,00 em quadros
 * consecutivos, com a pessoa parada. A causa era a junta do punho trocando de fonte: medida num
 * quadro, sintetizada em pose de repouso no seguinte. Estes testes fixam as duas metades da correção —
 * segurar a última medição por uma janela curta, e congelar o gesto quando não há medição.
 */
public class SkeletonContinuityTest {

  private static final float STATURE = 1.75f;
  private static final float MEASURED_WRIST_Y = 1.55f;
  private static final float SHOULDER_Y = 1.43f;

  /** Ponto de partida: punho medido bem acima do ombro, como num braço erguido. */
  private static MetricSkeleton raisedArmSkeleton() {
    MetricSkeleton skeleton = new MetricSkeleton();
    skeleton.valid = true;
    skeleton.trackId = 1;
    skeleton.statureM = STATURE;
    skeleton.set(MetricSkeleton.LEFT_SHOULDER, 0.21f, SHOULDER_Y, 2.5f,
        GeodesicSkeletonFitter.CONFIDENCE_PATH);
    skeleton.set(MetricSkeleton.RIGHT_SHOULDER, -0.21f, SHOULDER_Y, 2.5f,
        GeodesicSkeletonFitter.CONFIDENCE_PATH);
    skeleton.set(MetricSkeleton.LEFT_WRIST, 0.23f, MEASURED_WRIST_Y, 2.5f,
        GeodesicSkeletonFitter.CONFIDENCE_MEASURED);
    skeleton.set(MetricSkeleton.RIGHT_WRIST, -0.23f, MEASURED_WRIST_Y, 2.5f,
        GeodesicSkeletonFitter.CONFIDENCE_MEASURED);
    return skeleton;
  }

  /** Simula o que LimbResolver faz quando perde o braço: punho em repouso, confiança sintética. */
  private static void synthesizeRestingLeftWrist(MetricSkeleton skeleton) {
    skeleton.set(MetricSkeleton.LEFT_WRIST, 0.21f, 0.85f, 2.5f,
        GeodesicSkeletonFitter.CONFIDENCE_SYNTHETIC);
  }

  @Test
  public void holdsTheLastMeasuredWristWhenTheLimbIsLostForOneFrame() {
    SkeletonContinuity continuity = new SkeletonContinuity();
    MetricSkeleton skeleton = raisedArmSkeleton();
    continuity.apply(skeleton);

    synthesizeRestingLeftWrist(skeleton);
    int held = continuity.apply(skeleton);

    assertTrue("deve segurar ao menos o punho perdido: " + held, held >= 1);
    assertEquals(
        "punho segurado deve voltar para a altura medida",
        MEASURED_WRIST_Y,
        skeleton.y(MetricSkeleton.LEFT_WRIST),
        0.001f);
    assertTrue(
        "junta segurada nao pode se passar por medida, conf "
            + skeleton.confidence(MetricSkeleton.LEFT_WRIST),
        skeleton.confidence(MetricSkeleton.LEFT_WRIST)
            < GeodesicSkeletonFitter.CONFIDENCE_PATH);
  }

  /**
   * A tolerância tem de acabar.
   *
   * <p>Insistir para sempre na última posição deixaria o braço congelado no ar e o consumidor sem
   * como distinguir isso de um braço realmente parado.
   */
  @Test
  public void releasesTheJointAfterTheHoldWindowExpires() {
    SkeletonContinuity continuity = new SkeletonContinuity();
    MetricSkeleton skeleton = raisedArmSkeleton();
    continuity.apply(skeleton);
    for (int frame = 0; frame < 40; frame++) {
      synthesizeRestingLeftWrist(skeleton);
      continuity.apply(skeleton);
    }
    assertEquals(
        "depois da janela o valor sintetizado passa",
        0.85f,
        skeleton.y(MetricSkeleton.LEFT_WRIST),
        0.001f);
    assertTrue(
        "e a confianca volta a ser sintetica",
        skeleton.confidence(MetricSkeleton.LEFT_WRIST)
            < GeodesicSkeletonFitter.CONFIDENCE_USABLE);
  }

  /**
   * O sintoma original: elevação saltando de quadro a quadro sem ninguém mexer o braço.
   *
   * <p>Roda os dois estágios na ordem de produção — continuidade e depois gestos — alternando quadro
   * medido com quadro perdido, e exige que a elevação publicada não oscile.
   */
  @Test
  public void armElevationStopsFlippingBetweenMeasuredAndSynthesized() {
    SkeletonContinuity continuity = new SkeletonContinuity();
    MetricGestureEngine gestures = new MetricGestureEngine();
    TrackingResult out = new TrackingResult();
    MetricSkeleton skeleton = raisedArmSkeleton();

    float minElevation = Float.MAX_VALUE;
    float maxElevation = -Float.MAX_VALUE;
    for (int frame = 0; frame < 8; frame++) {
      if (frame % 2 == 1) {
        synthesizeRestingLeftWrist(skeleton);
      } else {
        skeleton.set(MetricSkeleton.LEFT_WRIST, 0.23f, MEASURED_WRIST_Y, 2.5f,
            GeodesicSkeletonFitter.CONFIDENCE_MEASURED);
      }
      skeleton.timestampMs = 1000L + (frame * 33L);
      continuity.apply(skeleton);
      gestures.update(skeleton);
      gestures.applyTo(out);
      // O contrato antigo troca os lados: o punho anatomico esquerdo sai como mao direita da imagem.
      minElevation = Math.min(minElevation, out.rightHandElevation);
      maxElevation = Math.max(maxElevation, out.rightHandElevation);
    }
    assertTrue(
        String.format("elevacao oscilou de %.2f a %.2f", minElevation, maxElevation),
        (maxElevation - minElevation) < 0.10f);
    assertTrue("braco erguido deve continuar erguido", minElevation > 0.5f);
  }
}
