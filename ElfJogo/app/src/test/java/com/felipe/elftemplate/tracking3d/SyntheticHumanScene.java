package com.felipe.elftemplate.tracking3d;

/**
 * Monta um corpo humano articulado em cápsulas, com proporções reais e pose configurável.
 *
 * <p>As posições vêm de {@link BodyProportions}, então o fixture e o motor compartilham a mesma
 * antropometria e um teste que passa significa "o motor recuperou a geometria que foi renderizada",
 * não "o motor concorda consigo mesmo".
 *
 * <p>Lateralidade anatômica: o lado esquerdo da pessoa fica em X maior, porque ela está de frente
 * para a câmera.
 */
final class SyntheticHumanScene {

  enum Pose {
    ARMS_DOWN,
    T_POSE,
    HANDS_UP,
    LEFT_ARM_UP
  }

  /** Raios de referência para estatura 1,70 m; escalam linearmente com a estatura. */
  private static final float REFERENCE_STATURE = 1.70f;

  /**
   * Raios de circunferência real: tronco 0,105 m de espessura com barra de peito para a largura,
   * braço 0,055 (circunferência ~35 cm), antebraço 0,045. Manter os degraus entre segmentos vizinhos
   * pequenos importa: um corpo humano é uma superfície contínua, e um fixture com ombro fino demais
   * criaria um salto de profundidade que nem o sensor nem a anatomia produzem.
   */
  private static final float TORSO_RADIUS = 0.105f;

  private static final float SHOULDER_RADIUS = 0.070f;
  private static final float HEAD_RADIUS = 0.092f;
  private static final float NECK_RADIUS = 0.058f;
  private static final float UPPER_ARM_RADIUS = 0.055f;
  private static final float FOREARM_RADIUS = 0.045f;
  private static final float THIGH_RADIUS = 0.080f;
  private static final float SHANK_RADIUS = 0.055f;

  /** Braços relaxados ficam ligeiramente à frente do tronco, como numa postura real de pé. */
  private static final float RELAXED_ARM_FORWARD_M = 0.10f;

  private final SyntheticDepthRenderer renderer;
  private float stature;
  private float depth;
  private float lateral;
  private float scale;

  SyntheticHumanScene(SyntheticDepthRenderer depthRenderer) {
    this.renderer = depthRenderer;
  }

  /**
   * Adiciona uma pessoa à cena.
   *
   * @param statureM estatura real em metros
   * @param depthM distância horizontal do tronco em metros
   * @param lateralM deslocamento lateral do eixo do corpo em metros
   */
  void addPerson(float statureM, float depthM, float lateralM, Pose pose) {
    this.stature = statureM;
    this.depth = depthM;
    this.lateral = lateralM;
    this.scale = statureM / REFERENCE_STATURE;
    addTorsoAndHead();
    addArm(pose, true);
    addArm(pose, false);
    addLeg(true);
    addLeg(false);
  }

  /** Tronco como coluna vertical mais barras de peito e abdômen, para largura sem virar cilindro. */
  private void addTorsoAndHead() {
    float shoulderY = BodyProportions.SHOULDER_HEIGHT * stature;
    float hipY = BodyProportions.HIP_HEIGHT * stature;
    float headY = BodyProportions.HEAD_CENTER_HEIGHT * stature;
    float shoulderHalf = BodyProportions.SHOULDER_HALF_SPAN * stature;
    float chestY = shoulderY - (0.14f * scale);
    capsule(lateral, hipY, lateral, shoulderY, TORSO_RADIUS);
    capsule(lateral - (0.07f * scale), chestY, lateral + (0.07f * scale), chestY, TORSO_RADIUS);
    capsule(lateral - (0.05f * scale), hipY, lateral + (0.05f * scale), hipY, TORSO_RADIUS);
    capsule(
        lateral - shoulderHalf, shoulderY, lateral + shoulderHalf, shoulderY, SHOULDER_RADIUS);
    capsule(lateral, shoulderY, lateral, headY - (0.07f * scale), NECK_RADIUS);
    capsule(lateral, headY - (0.03f * scale), lateral, headY + (0.03f * scale), HEAD_RADIUS);
  }

  /** Braço em três poses distintas, sempre com comprimentos antropométricos corretos. */
  private void addArm(Pose pose, boolean anatomicalLeft) {
    float side = anatomicalLeft ? 1f : -1f;
    float shoulderY = BodyProportions.SHOULDER_HEIGHT * stature;
    float shoulderX = lateral + (side * BodyProportions.SHOULDER_HALF_SPAN * stature);
    float upperArm = BodyProportions.UPPER_ARM_LENGTH * stature;
    float forearm = BodyProportions.FOREARM_LENGTH * stature;
    boolean raised = pose == Pose.HANDS_UP || (pose == Pose.LEFT_ARM_UP && anatomicalLeft);
    if (pose == Pose.T_POSE) {
      addHorizontalArm(shoulderX, shoulderY, side, upperArm, forearm);
      return;
    }
    if (raised) {
      addVerticalArm(shoulderX, shoulderY, upperArm, forearm, 1f);
      return;
    }
    addRelaxedArm(shoulderX, shoulderY, side, upperArm, forearm);
  }

  private void addHorizontalArm(
      float shoulderX, float shoulderY, float side, float upperArm, float forearm) {
    float elbowX = shoulderX + (side * upperArm);
    float wristX = elbowX + (side * forearm);
    capsule(shoulderX, shoulderY, elbowX, shoulderY, UPPER_ARM_RADIUS);
    capsule(elbowX, shoulderY, wristX, shoulderY, FOREARM_RADIUS);
  }

  private void addVerticalArm(
      float shoulderX, float shoulderY, float upperArm, float forearm, float direction) {
    float elbowY = shoulderY + (direction * upperArm);
    float wristY = elbowY + (direction * forearm);
    capsule(shoulderX, shoulderY, shoulderX, elbowY, UPPER_ARM_RADIUS);
    capsule(shoulderX, elbowY, shoulderX, wristY, FOREARM_RADIUS);
  }

  /** Braço relaxado: levemente aberto e à frente do tronco, como uma pessoa em pé de verdade. */
  private void addRelaxedArm(
      float shoulderX, float shoulderY, float side, float upperArm, float forearm) {
    float elbowX = shoulderX + (side * 0.04f * scale);
    float elbowY = shoulderY - upperArm;
    float wristX = elbowX + (side * 0.02f * scale);
    float wristY = elbowY - forearm;
    float forwardZ = depth - (RELAXED_ARM_FORWARD_M * scale);
    capsuleAt(shoulderX, shoulderY, depth, elbowX, elbowY, forwardZ, UPPER_ARM_RADIUS);
    capsuleAt(elbowX, elbowY, forwardZ, wristX, wristY, forwardZ, FOREARM_RADIUS);
  }

  private void addLeg(boolean anatomicalLeft) {
    float side = anatomicalLeft ? 1f : -1f;
    float hipX = lateral + (side * BodyProportions.HIP_HALF_SPAN * stature);
    float hipY = BodyProportions.HIP_HEIGHT * stature;
    float kneeY = BodyProportions.KNEE_HEIGHT * stature;
    float ankleY = BodyProportions.ANKLE_HEIGHT * stature;
    capsule(hipX, hipY, hipX, kneeY, THIGH_RADIUS);
    capsule(hipX, kneeY, hipX, ankleY, SHANK_RADIUS);
  }

  /** Cápsula no plano frontal do corpo (Z constante). */
  private void capsule(float x1, float y1, float x2, float y2, float radius) {
    capsuleAt(x1, y1, depth, x2, y2, depth, radius);
  }

  private void capsuleAt(
      float x1, float y1, float z1, float x2, float y2, float z2, float radius) {
    renderer.addWorldCapsule(x1, y1, z1, x2, y2, z2, radius * scale);
  }

  /** Posição real do punho, para comparar com o que o motor estima. */
  float truthWristY(Pose pose, boolean anatomicalLeft) {
    float shoulderY = BodyProportions.SHOULDER_HEIGHT * stature;
    float armLength = BodyProportions.ARM_LENGTH * stature;
    boolean raised = pose == Pose.HANDS_UP || (pose == Pose.LEFT_ARM_UP && anatomicalLeft);
    if (pose == Pose.T_POSE) {
      return shoulderY;
    }
    return raised ? shoulderY + armLength : shoulderY - armLength;
  }

  /** X real do punho, para comparar com o que o motor estima. */
  float truthWristX(Pose pose, boolean anatomicalLeft) {
    float side = anatomicalLeft ? 1f : -1f;
    float shoulderX = lateral + (side * BodyProportions.SHOULDER_HALF_SPAN * stature);
    if (pose == Pose.T_POSE) {
      return shoulderX + (side * BodyProportions.ARM_LENGTH * stature);
    }
    return shoulderX + (side * 0.06f * scale);
  }
}
