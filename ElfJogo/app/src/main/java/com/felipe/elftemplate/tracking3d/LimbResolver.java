package com.felipe.elftemplate.tracking3d;

/**
 * Resolve braços e pernas a partir dos extremos geodésicos e dos caminhos que levam até eles.
 *
 * <p>Cotovelo e joelho saem do percurso real do membro: caminhando de volta pelo caminho geodésico a
 * partir do punho ou do tornozelo, na distância antropométrica do segmento. Com o braço dobrado o
 * percurso continua tendo o comprimento do braço, então o cotovelo cai no lugar certo. O pipeline
 * antigo usava o ponto médio entre ombro e mão, que só coincide com o cotovelo quando o braço está
 * completamente estendido.
 *
 * <p>Também aceita braço cruzando o corpo: o lado de cada mão é decidido pelo ombro onde o caminho
 * dela termina, não pela posição lateral da mão. O sistema antigo travava a mão no próprio ombro e
 * tornava qualquer gesto cross-body impossível.
 */
public final class LimbResolver {

  /** Extremos a menos disto da cabeça são a própria cabeça, não mão. */
  private static final float HEAD_EXCLUSION_M = 0.20f;

  /** Altura relativa abaixo da qual um extremo é pé, não mão. */
  private static final float FOOT_HEIGHT_RATIO = 0.25f;

  /**
   * Altura relativa mínima da âncora para o extremo ser aceito como mão.
   *
   * <p>Andando de volta o comprimento de um braço a partir de um punho, chega-se ao ombro (0,82 da
   * estatura). A partir de um tornozelo chega-se à coxa, e a partir de um joelho chega-se à pelve —
   * ambos bem abaixo. Esse é o teste que separa membro superior de inferior, e ele funciona com o braço
   * relaxado, erguido ou na horizontal. Uma janela de distância geodésica não separa: a perna é mais
   * longa que o braço, então joelho e punho relaxado caem no mesmo intervalo.
   */
  private static final float HAND_ANCHOR_HEIGHT_RATIO = 0.70f;

  /** Tolerância de altura para confiar no ombro vindo do caminho do braço. */
  private static final float SHOULDER_PATH_TOLERANCE_M = 0.18f;

  private int leftHandCell = -1;
  private int rightHandCell = -1;
  private int leftFootCell = -1;
  private int rightFootCell = -1;
  private boolean feetVisible;

  /** Resolve os quatro membros e escreve as juntas correspondentes no esqueleto. */
  public void resolve(
      DepthPointCloud cloud,
      MetricBodySegmenter segmenter,
      GeodesicField field,
      MetricSkeleton skeleton,
      float axisX) {
    leftHandCell = -1;
    rightHandCell = -1;
    leftFootCell = -1;
    rightFootCell = -1;
    feetVisible = false;
    classifyExtremities(cloud, segmenter, field, skeleton, axisX);
    applyArm(cloud, segmenter, field, skeleton, true);
    applyArm(cloud, segmenter, field, skeleton, false);
    applyLeg(cloud, segmenter, field, skeleton, true);
    applyLeg(cloud, segmenter, field, skeleton, false);
  }

  /**
   * Separa os extremos em cabeça, mãos e pés usando altura e distância geodésica esperada.
   *
   * <p>Um extremo só é aceito como mão se o percurso até ele tiver, de fato, o comprimento de um
   * braço. Isso descarta cotovelos, dobras de roupa e objetos ainda encostados no corpo.
   */
  private void classifyExtremities(
      DepthPointCloud cloud,
      MetricBodySegmenter segmenter,
      GeodesicField field,
      MetricSkeleton skeleton,
      float axisX) {
    float stature = skeleton.statureM;
    float footHeight = FOOT_HEIGHT_RATIO * stature;
    float minAnchorHeight = HAND_ANCHOR_HEIGHT_RATIO * stature;
    int armLengthMm = (int) (BodyProportions.ARM_LENGTH * stature * 1000f);
    for (int e = 0; e < field.getExtremityCount(); e++) {
      int cell = field.getExtremity(e);
      if (isHeadRegion(cloud, segmenter, skeleton, cell)) {
        continue;
      }
      int anchor = field.walkBack(cell, armLengthMm);
      if (segmenter.worldYAt(anchor) >= minAnchorHeight) {
        assignHand(cloud, field, anchor, cell, axisX);
      } else if (segmenter.worldYAt(cell) < footHeight) {
        assignFoot(cloud, cell, axisX);
      }
    }
    feetVisible = leftFootCell >= 0 || rightFootCell >= 0;
  }

  private boolean isHeadRegion(
      DepthPointCloud cloud,
      MetricBodySegmenter segmenter,
      MetricSkeleton skeleton,
      int cell) {
    float dx = cloud.x(cell) - skeleton.x(MetricSkeleton.HEAD);
    float dy = segmenter.worldYAt(cell) - skeleton.y(MetricSkeleton.HEAD);
    float dz = segmenter.worldZAt(cell) - skeleton.z(MetricSkeleton.HEAD);
    return ((dx * dx) + (dy * dy) + (dz * dz)) <= (HEAD_EXCLUSION_M * HEAD_EXCLUSION_M);
  }

  /** Pés são separados pela posição lateral: pernas não cruzam o eixo em uso normal. */
  private void assignFoot(DepthPointCloud cloud, int cell, float axisX) {
    boolean anatomicalLeft = cloud.x(cell) >= axisX;
    if (anatomicalLeft) {
      leftFootCell = leftFootCell < 0 ? cell : leftFootCell;
      return;
    }
    rightFootCell = rightFootCell < 0 ? cell : rightFootCell;
  }

  /**
   * Decide o lado da mão pelo ombro onde o caminho do braço termina.
   *
   * <p>Caminha de volta o comprimento de um braço e observa em que lado do eixo do corpo esse ponto
   * caiu. Uma mão levada para o outro lado do corpo continua pertencendo ao braço de origem.
   */
  private void assignHand(
      DepthPointCloud cloud, GeodesicField field, int shoulderAnchor, int cell, float axisX) {
    boolean anatomicalLeft = cloud.x(shoulderAnchor) >= axisX;
    if (anatomicalLeft) {
      leftHandCell = preferFarther(field, leftHandCell, cell);
      rightHandCell = demoteRunnerUp(rightHandCell, leftHandCell, cell);
      return;
    }
    rightHandCell = preferFarther(field, rightHandCell, cell);
    leftHandCell = demoteRunnerUp(leftHandCell, rightHandCell, cell);
  }

  private static int preferFarther(GeodesicField field, int current, int candidate) {
    if (current < 0) {
      return candidate;
    }
    return field.distanceMm(candidate) > field.distanceMm(current) ? candidate : current;
  }

  /**
   * Guarda o candidato perdedor no lado oposto quando aquele lado ainda está vazio.
   *
   * <p>Acontece quando as duas mãos se juntam no meio do corpo e os dois caminhos terminam no mesmo
   * ombro. Perder uma das mãos seria pior que atribuí-la ao lado livre.
   */
  private static int demoteRunnerUp(int oppositeCurrent, int winner, int candidate) {
    if (oppositeCurrent >= 0 || winner == candidate) {
      return oppositeCurrent;
    }
    return candidate;
  }

  /** Escreve punho, cotovelo e refinamento de ombro para um dos braços. */
  private void applyArm(
      DepthPointCloud cloud,
      MetricBodySegmenter segmenter,
      GeodesicField field,
      MetricSkeleton skeleton,
      boolean anatomicalLeft) {
    int wristJoint = anatomicalLeft ? MetricSkeleton.LEFT_WRIST : MetricSkeleton.RIGHT_WRIST;
    int elbowJoint = anatomicalLeft ? MetricSkeleton.LEFT_ELBOW : MetricSkeleton.RIGHT_ELBOW;
    int shoulderJoint =
        anatomicalLeft ? MetricSkeleton.LEFT_SHOULDER : MetricSkeleton.RIGHT_SHOULDER;
    int handCell = anatomicalLeft ? leftHandCell : rightHandCell;
    if (handCell < 0) {
      synthesizeRelaxedArm(skeleton, wristJoint, elbowJoint, shoulderJoint);
      return;
    }
    setFromCell(cloud, segmenter, skeleton, wristJoint, handCell, GeodesicSkeletonFitter.CONFIDENCE_MEASURED);
    int forearmMm = (int) (BodyProportions.FOREARM_LENGTH * skeleton.statureM * 1000f);
    int elbowCell = field.walkBack(handCell, forearmMm);
    setFromCell(cloud, segmenter, skeleton, elbowJoint, elbowCell, GeodesicSkeletonFitter.CONFIDENCE_PATH);
    refineShoulder(cloud, segmenter, field, skeleton, shoulderJoint, handCell);
  }

  /**
   * Substitui o ombro proporcional pelo ombro do caminho quando a altura confere.
   *
   * <p>Se o caminho terminar longe da altura esperada de ombro, o braço estava parcialmente oculto e
   * a estimativa proporcional é a melhor disponível.
   */
  private void refineShoulder(
      DepthPointCloud cloud,
      MetricBodySegmenter segmenter,
      GeodesicField field,
      MetricSkeleton skeleton,
      int shoulderJoint,
      int handCell) {
    int armLengthMm = (int) (BodyProportions.ARM_LENGTH * skeleton.statureM * 1000f);
    int shoulderCell = field.walkBack(handCell, armLengthMm);
    float pathY = segmenter.worldYAt(shoulderCell);
    if (Math.abs(pathY - skeleton.y(shoulderJoint)) > SHOULDER_PATH_TOLERANCE_M) {
      return;
    }
    setFromCell(
        cloud, segmenter, skeleton, shoulderJoint, shoulderCell, GeodesicSkeletonFitter.CONFIDENCE_PATH);
  }

  /** Braço não detectado: posiciona o membro relaxado ao lado do corpo, com confiança baixa. */
  private void synthesizeRelaxedArm(
      MetricSkeleton skeleton, int wristJoint, int elbowJoint, int shoulderJoint) {
    float stature = skeleton.statureM;
    float shoulderX = skeleton.x(shoulderJoint);
    float headY = skeleton.y(MetricSkeleton.HEAD);
    float elbowY =
        headY - ((BodyProportions.HEAD_CENTER_HEIGHT - BodyProportions.ELBOW_HEIGHT) * stature);
    float wristY =
        headY - ((BodyProportions.HEAD_CENTER_HEIGHT - BodyProportions.WRIST_HEIGHT) * stature);
    float depth = skeleton.z(shoulderJoint);
    skeleton.set(
        elbowJoint, shoulderX, elbowY, depth, GeodesicSkeletonFitter.CONFIDENCE_SYNTHETIC);
    skeleton.set(
        wristJoint, shoulderX, wristY, depth, GeodesicSkeletonFitter.CONFIDENCE_SYNTHETIC);
  }

  /** Escreve tornozelo e joelho para uma das pernas. */
  private void applyLeg(
      DepthPointCloud cloud,
      MetricBodySegmenter segmenter,
      GeodesicField field,
      MetricSkeleton skeleton,
      boolean anatomicalLeft) {
    int ankleJoint = anatomicalLeft ? MetricSkeleton.LEFT_ANKLE : MetricSkeleton.RIGHT_ANKLE;
    int kneeJoint = anatomicalLeft ? MetricSkeleton.LEFT_KNEE : MetricSkeleton.RIGHT_KNEE;
    int hipJoint = anatomicalLeft ? MetricSkeleton.LEFT_HIP : MetricSkeleton.RIGHT_HIP;
    int footCell = anatomicalLeft ? leftFootCell : rightFootCell;
    if (footCell < 0) {
      synthesizeStandingLeg(skeleton, ankleJoint, kneeJoint, hipJoint);
      return;
    }
    setFromCell(cloud, segmenter, skeleton, ankleJoint, footCell, GeodesicSkeletonFitter.CONFIDENCE_MEASURED);
    int shankMm = (int) (BodyProportions.SHANK_LENGTH * skeleton.statureM * 1000f);
    int kneeCell = field.walkBack(footCell, shankMm);
    setFromCell(cloud, segmenter, skeleton, kneeJoint, kneeCell, GeodesicSkeletonFitter.CONFIDENCE_PATH);
  }

  private void synthesizeStandingLeg(
      MetricSkeleton skeleton, int ankleJoint, int kneeJoint, int hipJoint) {
    float stature = skeleton.statureM;
    float hipX = skeleton.x(hipJoint);
    float depth = skeleton.z(hipJoint);
    skeleton.set(
        kneeJoint,
        hipX,
        BodyProportions.KNEE_HEIGHT * stature,
        depth,
        GeodesicSkeletonFitter.CONFIDENCE_SYNTHETIC);
    skeleton.set(
        ankleJoint,
        hipX,
        BodyProportions.ANKLE_HEIGHT * stature,
        depth,
        GeodesicSkeletonFitter.CONFIDENCE_SYNTHETIC);
  }

  private static void setFromCell(
      DepthPointCloud cloud,
      MetricBodySegmenter segmenter,
      MetricSkeleton skeleton,
      int joint,
      int cell,
      float confidence) {
    skeleton.set(
        joint, cloud.x(cell), segmenter.worldYAt(cell), segmenter.worldZAt(cell), confidence);
  }

  /** True quando pelo menos um pé foi efetivamente encontrado como extremo geodésico. */
  public boolean areFeetVisible() {
    return feetVisible;
  }
}
