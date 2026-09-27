package com.felipe.elftemplate.tracking3d;

import java.util.Arrays;

/**
 * Segura a última posição medida de cada junta quando a medição falha por alguns quadros.
 *
 * <p>Corrige um defeito observado no robô, não uma hipótese. No logcat do Modo Espelho a elevação do
 * braço oscilava assim, em menos de um segundo:
 *
 * <pre>
 *   elev=0,00 → 0,88 → 0,24 → 1,00 → 0,00
 * </pre>
 *
 * <p>Ninguém move o braço desse jeito. O que acontecia é que a junta trocava de fonte a cada quadro:
 * num quadro o classificador ou o caminho geodésico media o punho, no seguinte nenhum dos dois
 * conseguia e {@code LimbResolver} sintetizava um braço em repouso ao lado do corpo. Medido e
 * inventado se alternando produz um salto de mais de meio metro por quadro, e como esse salto passa do
 * limiar de teleporte do filtro One-Euro, o filtro se reinicializava em vez de suavizar — a asa do robô
 * recebia o pulo inteiro.
 *
 * <p>A solução é a mesma semântica que o SDK do Kinect expõe como {@code Inferred}: enquanto a
 * medição não volta, mantém-se a última posição conhecida e a confiança cai. Assim uma falha de um ou
 * dois quadros não vira movimento, e uma ausência prolongada é reportada como ausência de verdade em
 * vez de virar uma pose inventada com cara de medição.
 */
public final class SkeletonContinuity {

  /**
   * Quadros de tolerância antes de desistir de uma junta.
   *
   * <p>12 quadros a 30 fps são 400 ms. Cobre oclusão curta e falha de segmentação sem chegar perto do
   * tempo de um gesto: um braço que sobe leva mais que isso, então nada de movimento real é escondido.
   */
  private static final int HOLD_FRAMES = 12;

  /** Confiança de uma junta recém-segurada, logo abaixo da faixa de medição. */
  private static final float HELD_CONFIDENCE_CEILING = 0.65f;

  /** Confiança no último quadro de tolerância; abaixo do limiar de uso dos consumidores. */
  private static final float HELD_CONFIDENCE_FLOOR = 0.25f;

  private final float[] lastX = new float[MetricSkeleton.JOINT_COUNT];
  private final float[] lastY = new float[MetricSkeleton.JOINT_COUNT];
  private final float[] lastZ = new float[MetricSkeleton.JOINT_COUNT];

  /** Quadros desde a última medição da junta; -1 significa "nunca foi medida". */
  private final int[] framesSinceMeasured = new int[MetricSkeleton.JOINT_COUNT];

  private int heldJoints;
  private int measuredJoints;

  public SkeletonContinuity() {
    reset();
  }

  /** Zera o histórico. Chamado quando o jogador sai de cena ou troca de identidade. */
  public void reset() {
    Arrays.fill(framesSinceMeasured, -1);
    heldJoints = 0;
    measuredJoints = 0;
  }

  /**
   * Aplica a continuidade no lugar.
   *
   * @return quantidade de juntas que estão sendo seguradas neste quadro
   */
  public int apply(MetricSkeleton skeleton) {
    heldJoints = 0;
    measuredJoints = 0;
    if (skeleton == null || !skeleton.valid) {
      reset();
      return 0;
    }
    for (int joint = 0; joint < MetricSkeleton.JOINT_COUNT; joint++) {
      if (skeleton.confidence(joint) >= GeodesicSkeletonFitter.CONFIDENCE_USABLE) {
        storeMeasured(skeleton, joint);
      } else {
        holdOrRelease(skeleton, joint);
      }
    }
    return heldJoints;
  }

  private void storeMeasured(MetricSkeleton skeleton, int joint) {
    lastX[joint] = skeleton.x(joint);
    lastY[joint] = skeleton.y(joint);
    lastZ[joint] = skeleton.z(joint);
    framesSinceMeasured[joint] = 0;
    measuredJoints++;
  }

  /**
   * Restaura a última medição enquanto houver tolerância; depois deixa passar o valor sintetizado.
   *
   * <p>Deixar passar é intencional. Depois de 400 ms sem ver o membro, insistir na última posição
   * seria pior que admitir o desconhecimento: o braço ficaria congelado no ar indefinidamente, e o
   * consumidor não teria como distinguir isso de um braço parado de verdade.
   */
  private void holdOrRelease(MetricSkeleton skeleton, int joint) {
    int age = framesSinceMeasured[joint];
    if (age < 0 || age >= HOLD_FRAMES) {
      return;
    }
    framesSinceMeasured[joint] = age + 1;
    skeleton.set(joint, lastX[joint], lastY[joint], lastZ[joint], heldConfidence(age));
    heldJoints++;
  }

  /** Confiança decaindo linearmente ao longo da janela de tolerância. */
  private static float heldConfidence(int age) {
    float progress = age / (float) HOLD_FRAMES;
    return HELD_CONFIDENCE_CEILING
        - ((HELD_CONFIDENCE_CEILING - HELD_CONFIDENCE_FLOOR) * progress);
  }

  public int getHeldJoints() {
    return heldJoints;
  }

  public int getMeasuredJoints() {
    return measuredJoints;
  }

  /** Quadros desde a última medição da junta; -1 se ela nunca foi medida nesta sessão. */
  public int framesSinceMeasured(int joint) {
    if (joint < 0 || joint >= MetricSkeleton.JOINT_COUNT) {
      return -1;
    }
    return framesSinceMeasured[joint];
  }
}
