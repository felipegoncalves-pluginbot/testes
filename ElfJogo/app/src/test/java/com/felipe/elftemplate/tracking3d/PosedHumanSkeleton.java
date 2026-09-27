package com.felipe.elftemplate.tracking3d;

/**
 * Corpo humano articulado a partir de ângulos, com as juntas de verdade em coordenadas de mundo.
 *
 * <p>Existe para gerar variedade de pose no treino do classificador de partes. O fixture antigo tinha
 * quatro poses fixas, o que serve para regressão e é inútil para treinar: uma floresta treinada em
 * quatro poses decora essas quatro. A robustez do Kinect vem de ter visto milhares de configurações,
 * então o gerador precisa produzir pose contínua, não um catálogo.
 *
 * <p><b>Frame local e yaw.</b> O corpo é montado num referencial próprio (X = lado esquerdo da
 * pessoa, Y = altura acima do piso, Z = para longe da câmera) e só no fim é rotacionado e transladado
 * para o mundo. Sem essa separação, cada ângulo de membro carregaria o yaw dentro dele e um erro de
 * sinal ficaria impossível de achar.
 *
 * <p><b>Contato com o chão.</b> Depois de montar tudo, o corpo inteiro desce ou sobe até o tornozelo
 * mais baixo encostar no piso. É o que mantém agachamento e passada fisicamente plausíveis sem
 * resolver cinemática inversa: a altura do quadril entra como parâmetro livre e o chão é imposto no
 * final.
 */
final class PosedHumanSkeleton {

  /** Fração do trecho ombro-cabeça onde fica o pescoço. */
  private static final float NECK_FRACTION_OF_HEAD_RUN = 0.35f;

  private final float[] localX = new float[MetricSkeleton.JOINT_COUNT];
  private final float[] localY = new float[MetricSkeleton.JOINT_COUNT];
  private final float[] localZ = new float[MetricSkeleton.JOINT_COUNT];
  private final float[] worldX = new float[MetricSkeleton.JOINT_COUNT];
  private final float[] worldY = new float[MetricSkeleton.JOINT_COUNT];
  private final float[] worldZ = new float[MetricSkeleton.JOINT_COUNT];

  private final float[] armElevation = new float[2];
  private final float[] armAzimuth = new float[2];
  private final float[] elbowFlex = new float[2];
  private final float[] hipFlex = new float[2];
  private final float[] hipAbduct = new float[2];
  private final float[] kneeFlex = new float[2];

  private float stature = 1.75f;
  private float hipHeightRatio = 1f;
  private float torsoLean;
  private float liftM;

  private static int sideIndex(boolean anatomicalLeft) {
    return anatomicalLeft ? 0 : 1;
  }

  void setBody(float statureM, float hipRatio, float torsoLeanRad, float verticalLiftM) {
    this.stature = statureM;
    this.hipHeightRatio = hipRatio;
    this.torsoLean = torsoLeanRad;
    this.liftM = verticalLiftM;
  }

  void setArm(boolean anatomicalLeft, float elevationRad, float azimuthRad, float flexRad) {
    int side = sideIndex(anatomicalLeft);
    armElevation[side] = elevationRad;
    armAzimuth[side] = azimuthRad;
    elbowFlex[side] = flexRad;
  }

  void setLeg(boolean anatomicalLeft, float flexRad, float abductRad, float kneeFlexRad) {
    int side = sideIndex(anatomicalLeft);
    hipFlex[side] = flexRad;
    hipAbduct[side] = abductRad;
    kneeFlex[side] = kneeFlexRad;
  }

  float getStature() {
    return stature;
  }

  /** Monta o corpo e escreve as juntas no mundo, com yaw e posição aplicados. */
  void build(float depthM, float lateralM, float yawRad) {
    buildTorso();
    solveSide(true);
    solveSide(false);
    applyGroundContact();
    toWorld(depthM, lateralM, yawRad);
  }

  private void solveSide(boolean anatomicalLeft) {
    int side = sideIndex(anatomicalLeft);
    LimbChainSolver.solveArm(
        this, anatomicalLeft, armElevation[side], armAzimuth[side], elbowFlex[side]);
    LimbChainSolver.solveLeg(
        this, anatomicalLeft, hipFlex[side], hipAbduct[side], kneeFlex[side]);
  }

  /** Tronco como cadeia para cima a partir do quadril, inclinável para frente ou para trás. */
  private void buildTorso() {
    float hipY = BodyProportions.HIP_HEIGHT * stature * hipHeightRatio;
    float shoulderRun = (BodyProportions.SHOULDER_HEIGHT - BodyProportions.HIP_HEIGHT) * stature;
    float headRun =
        (BodyProportions.HEAD_CENTER_HEIGHT - BodyProportions.SHOULDER_HEIGHT) * stature;
    float trunkY = (float) Math.cos(torsoLean);
    float trunkZ = -(float) Math.sin(torsoLean);
    float shoulderY = hipY + (trunkY * shoulderRun);
    float shoulderZ = trunkZ * shoulderRun;
    float halfSpan = BodyProportions.SHOULDER_HALF_SPAN * stature;
    float hipHalfSpan = BodyProportions.HIP_HALF_SPAN * stature;

    setLocal(MetricSkeleton.LEFT_HIP, hipHalfSpan, hipY, 0f);
    setLocal(MetricSkeleton.RIGHT_HIP, -hipHalfSpan, hipY, 0f);
    setLocal(
        MetricSkeleton.SPINE,
        0f,
        hipY + (trunkY * shoulderRun * 0.5f),
        trunkZ * shoulderRun * 0.5f);
    setLocal(MetricSkeleton.LEFT_SHOULDER, halfSpan, shoulderY, shoulderZ);
    setLocal(MetricSkeleton.RIGHT_SHOULDER, -halfSpan, shoulderY, shoulderZ);
    setLocal(
        MetricSkeleton.NECK,
        0f,
        shoulderY + (trunkY * headRun * NECK_FRACTION_OF_HEAD_RUN),
        shoulderZ + (trunkZ * headRun * NECK_FRACTION_OF_HEAD_RUN));
    setLocal(
        MetricSkeleton.HEAD, 0f, shoulderY + (trunkY * headRun), shoulderZ + (trunkZ * headRun));
  }

  /** Desloca o corpo até o tornozelo mais baixo tocar o piso, mais o salto pedido. */
  private void applyGroundContact() {
    float lowest = Math.min(localY[MetricSkeleton.LEFT_ANKLE], localY[MetricSkeleton.RIGHT_ANKLE]);
    float target = BodyProportions.ANKLE_HEIGHT * stature;
    float shift = (target - lowest) + liftM;
    for (int joint = 0; joint < MetricSkeleton.JOINT_COUNT; joint++) {
      localY[joint] = localY[joint] + shift;
    }
  }

  /** Aplica yaw em torno do eixo vertical e translada para a posição na sala. */
  private void toWorld(float depthM, float lateralM, float yawRad) {
    float cos = (float) Math.cos(yawRad);
    float sin = (float) Math.sin(yawRad);
    for (int joint = 0; joint < MetricSkeleton.JOINT_COUNT; joint++) {
      worldX[joint] = lateralM + ((localX[joint] * cos) - (localZ[joint] * sin));
      worldY[joint] = localY[joint];
      worldZ[joint] = depthM + ((localX[joint] * sin) + (localZ[joint] * cos));
    }
  }

  void setLocal(int joint, float x, float y, float z) {
    localX[joint] = x;
    localY[joint] = y;
    localZ[joint] = z;
  }

  float localX(int joint) {
    return localX[joint];
  }

  float localY(int joint) {
    return localY[joint];
  }

  float localZ(int joint) {
    return localZ[joint];
  }

  float x(int joint) {
    return worldX[joint];
  }

  float y(int joint) {
    return worldY[joint];
  }

  float z(int joint) {
    return worldZ[joint];
  }
}
