package com.felipe.elftemplate.tracking3d;

/**
 * Cadeias cinemáticas de braço e perna no referencial local do corpo.
 *
 * <p>Separado de {@link PosedHumanSkeleton} porque é a parte com trigonometria de verdade, e um erro
 * de sinal aqui produz pose anatomicamente impossível no conjunto de treino — o classificador
 * aprenderia a reconhecer um corpo que não existe. Isolado, dá para verificar cada cadeia sozinha.
 *
 * <p>Convenção do frame local: X é o lado esquerdo da pessoa, Y é a altura acima do piso, Z aponta
 * para longe da câmera. Logo "para frente", do ponto de vista de quem está de frente para o robô, é
 * {@code -Z}.
 */
final class LimbChainSolver {

  private LimbChainSolver() {}

  /**
   * Resolve um braço a partir da elevação, do azimute e da dobra do cotovelo.
   *
   * <p>A direção do braço vem de coordenadas esféricas: elevação -90° é braço pendurado, 0° é
   * horizontal (T-pose) e +90° é braço reto para cima. O azimute tira o braço do plano frontal, que é
   * o que gera gesto de alcançar para frente — a pose que o pipeline de blob nunca conseguia ler,
   * porque de frente o braço estendido some dentro da silhueta do tronco.
   */
  static void solveArm(
      PosedHumanSkeleton body,
      boolean anatomicalLeft,
      float elevationRad,
      float azimuthRad,
      float elbowFlexRad) {
    float lateralSign = anatomicalLeft ? 1f : -1f;
    int shoulder = anatomicalLeft ? MetricSkeleton.LEFT_SHOULDER : MetricSkeleton.RIGHT_SHOULDER;
    int elbow = anatomicalLeft ? MetricSkeleton.LEFT_ELBOW : MetricSkeleton.RIGHT_ELBOW;
    int wrist = anatomicalLeft ? MetricSkeleton.LEFT_WRIST : MetricSkeleton.RIGHT_WRIST;
    float stature = body.getStature();
    float cosElevation = (float) Math.cos(elevationRad);
    float dirX = lateralSign * cosElevation * (float) Math.cos(azimuthRad);
    float dirY = (float) Math.sin(elevationRad);
    float dirZ = -cosElevation * (float) Math.sin(azimuthRad);

    extend(body, shoulder, elbow, dirX, dirY, dirZ, BodyProportions.UPPER_ARM_LENGTH * stature);
    bend(body, elbow, wrist, dirX, dirY, dirZ, elbowFlexRad,
        BodyProportions.FOREARM_LENGTH * stature, -1f);
  }

  /** Resolve uma perna a partir da flexão e abdução do quadril e da flexão do joelho. */
  static void solveLeg(
      PosedHumanSkeleton body,
      boolean anatomicalLeft,
      float hipFlexRad,
      float hipAbductRad,
      float kneeFlexRad) {
    float lateralSign = anatomicalLeft ? 1f : -1f;
    int hip = anatomicalLeft ? MetricSkeleton.LEFT_HIP : MetricSkeleton.RIGHT_HIP;
    int knee = anatomicalLeft ? MetricSkeleton.LEFT_KNEE : MetricSkeleton.RIGHT_KNEE;
    int ankle = anatomicalLeft ? MetricSkeleton.LEFT_ANKLE : MetricSkeleton.RIGHT_ANKLE;
    float stature = body.getStature();
    float cosAbduct = (float) Math.cos(hipAbductRad);
    float dirX = lateralSign * (float) Math.sin(hipAbductRad);
    float dirY = -cosAbduct * (float) Math.cos(hipFlexRad);
    float dirZ = -cosAbduct * (float) Math.sin(hipFlexRad);

    extend(body, hip, knee, dirX, dirY, dirZ, BodyProportions.THIGH_LENGTH * stature);
    bend(body, knee, ankle, dirX, dirY, dirZ, kneeFlexRad,
        BodyProportions.SHANK_LENGTH * stature, 1f);
  }

  private static void extend(
      PosedHumanSkeleton body,
      int fromJoint,
      int toJoint,
      float dirX,
      float dirY,
      float dirZ,
      float length) {
    body.setLocal(
        toJoint,
        body.localX(fromJoint) + (dirX * length),
        body.localY(fromJoint) + (dirY * length),
        body.localZ(fromJoint) + (dirZ * length));
  }

  /**
   * Dobra um segmento a partir da direção do segmento anterior.
   *
   * <p>A dobra acontece no plano que contém o segmento anterior e a direção de referência, com a
   * componente paralela removida por Gram-Schmidt. O cotovelo dobra para frente ({@code
   * referenceZ = -1}) e o joelho para trás ({@code +1}), que é a anatomia real.
   */
  private static void bend(
      PosedHumanSkeleton body,
      int fromJoint,
      int toJoint,
      float alongX,
      float alongY,
      float alongZ,
      float flexRad,
      float length,
      float referenceZ) {
    float projection = alongZ * referenceZ;
    float perpX = -projection * alongX;
    float perpY = -projection * alongY;
    float perpZ = referenceZ - (projection * alongZ);
    float norm = (float) Math.sqrt((perpX * perpX) + (perpY * perpY) + (perpZ * perpZ));
    if (norm < 1e-4f) {
      // Segmento já aponta na direção de referência: dobra no plano vertical.
      perpX = 0f;
      perpY = 1f - (alongY * alongY);
      perpZ = -alongY * alongZ;
      norm = (float) Math.sqrt((perpY * perpY) + (perpZ * perpZ));
      if (norm < 1e-4f) {
        norm = 1f;
        perpY = 1f;
        perpZ = 0f;
      }
    }
    float cos = (float) Math.cos(flexRad);
    float sin = (float) Math.sin(flexRad) / norm;
    extend(
        body,
        fromJoint,
        toJoint,
        (alongX * cos) + (perpX * sin),
        (alongY * cos) + (perpY * sin),
        (alongZ * cos) + (perpZ * sin),
        length);
  }
}
