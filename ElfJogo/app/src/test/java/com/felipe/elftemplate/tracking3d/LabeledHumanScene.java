package com.felipe.elftemplate.tracking3d;

/**
 * Converte um esqueleto posado em cápsulas rotuladas com a parte do corpo de cada uma.
 *
 * <p>É daqui que sai a verdade de campo do classificador: o renderizador traça o raio, descobre qual
 * cápsula foi atingida e grava o rótulo dela no pixel. Como as cápsulas são construídas a partir das
 * mesmas juntas que o teste depois usa para medir erro, imagem, rótulo e verdade nunca discordam.
 *
 * <p><b>Cada articulação é a sua própria cápsula.</b> Um membro não é uma cápsula só: é um eixo com o
 * rótulo do segmento (braço, antebraço, coxa, perna) mais uma cápsula curta na ponta com o rótulo da
 * articulação (cotovelo, mão, joelho, pé). Sem essa separação a nuvem de pixels de "mão" se
 * espalharia por todo o antebraço e o modo do mean shift cairia no meio do braço em vez de na mão.
 * O artigo do Kinect divide o corpo em 31 partes exatamente por isso.
 */
final class LabeledHumanScene {

  /** Raios de referência para estatura 1,70 m, iguais aos do fixture de regressão. */
  private static final float REFERENCE_STATURE = 1.70f;

  private static final float TORSO_RADIUS = 0.105f;
  private static final float HEAD_RADIUS = 0.092f;
  private static final float NECK_RADIUS = 0.058f;
  private static final float SHOULDER_RADIUS = 0.070f;
  private static final float UPPER_ARM_RADIUS = 0.055f;
  private static final float FOREARM_RADIUS = 0.045f;
  private static final float THIGH_RADIUS = 0.080f;
  private static final float SHANK_RADIUS = 0.055f;

  /**
   * Zona articular é mais gorda que o eixo por {@value #JOINT_BULGE_M} m.
   *
   * <p>É o que garante que a zona ganhe o raio na vizinhança da junta, e é também anatomicamente
   * correto: articulação é mais grossa que o segmento vizinho.
   */
  private static final float JOINT_BULGE_M = 0.012f;

  private static final float ELBOW_RADIUS = UPPER_ARM_RADIUS + JOINT_BULGE_M;
  private static final float HAND_RADIUS = FOREARM_RADIUS + JOINT_BULGE_M;
  private static final float KNEE_RADIUS = THIGH_RADIUS + JOINT_BULGE_M;
  private static final float FOOT_RADIUS = SHANK_RADIUS + JOINT_BULGE_M;
  private static final float HIP_RADIUS = 0.090f;

  /** Comprimento da zona articular na ponta de cada segmento, como fração da estatura. */
  private static final float JOINT_ZONE_RATIO = 0.055f;

  private final SyntheticDepthRenderer renderer;
  private float scale = 1f;
  private float zoneLength = 0.10f;

  LabeledHumanScene(SyntheticDepthRenderer depthRenderer) {
    this.renderer = depthRenderer;
  }

  /** Emite todas as cápsulas rotuladas do corpo informado. */
  void emit(PosedHumanSkeleton body) {
    this.scale = body.getStature() / REFERENCE_STATURE;
    this.zoneLength = JOINT_ZONE_RATIO * body.getStature();
    emitTorso(body);
    emitArm(body, true);
    emitArm(body, false);
    emitLeg(body, true);
    emitLeg(body, false);
  }

  private void emitTorso(PosedHumanSkeleton body) {
    float shoulderMidX = mid(body, MetricSkeleton.LEFT_SHOULDER, MetricSkeleton.RIGHT_SHOULDER, 0);
    float shoulderMidY = mid(body, MetricSkeleton.LEFT_SHOULDER, MetricSkeleton.RIGHT_SHOULDER, 1);
    float shoulderMidZ = mid(body, MetricSkeleton.LEFT_SHOULDER, MetricSkeleton.RIGHT_SHOULDER, 2);
    float hipMidX = mid(body, MetricSkeleton.LEFT_HIP, MetricSkeleton.RIGHT_HIP, 0);
    float hipMidY = mid(body, MetricSkeleton.LEFT_HIP, MetricSkeleton.RIGHT_HIP, 1);
    float hipMidZ = mid(body, MetricSkeleton.LEFT_HIP, MetricSkeleton.RIGHT_HIP, 2);
    float spineX = body.x(MetricSkeleton.SPINE);
    float spineY = body.y(MetricSkeleton.SPINE);
    float spineZ = body.z(MetricSkeleton.SPINE);

    capsule(shoulderMidX, shoulderMidY, shoulderMidZ, spineX, spineY, spineZ, TORSO_RADIUS,
        BodyPart.CHEST);
    capsule(spineX, spineY, spineZ, hipMidX, hipMidY, hipMidZ, TORSO_RADIUS, BodyPart.BELLY);
    // Barra entre os ombros: dá largura real ao tórax e acompanha o yaw do corpo sozinha.
    capsuleBetween(body, MetricSkeleton.LEFT_SHOULDER, MetricSkeleton.RIGHT_SHOULDER, TORSO_RADIUS,
        BodyPart.CHEST, 0.55f);
    capsule(body.x(MetricSkeleton.NECK), body.y(MetricSkeleton.NECK), body.z(MetricSkeleton.NECK),
        shoulderMidX, shoulderMidY, shoulderMidZ, NECK_RADIUS, BodyPart.NECK);
    zoneAt(body, MetricSkeleton.HEAD, HEAD_RADIUS, BodyPart.HEAD);
  }

  private void emitArm(PosedHumanSkeleton body, boolean anatomicalLeft) {
    int shoulder = anatomicalLeft ? MetricSkeleton.LEFT_SHOULDER : MetricSkeleton.RIGHT_SHOULDER;
    int elbow = anatomicalLeft ? MetricSkeleton.LEFT_ELBOW : MetricSkeleton.RIGHT_ELBOW;
    int wrist = anatomicalLeft ? MetricSkeleton.LEFT_WRIST : MetricSkeleton.RIGHT_WRIST;
    zoneAt(body, shoulder, SHOULDER_RADIUS,
        anatomicalLeft ? BodyPart.LEFT_SHOULDER : BodyPart.RIGHT_SHOULDER);
    limb(body, shoulder, elbow,
        anatomicalLeft ? BodyPart.LEFT_UPPER_ARM : BodyPart.RIGHT_UPPER_ARM,
        anatomicalLeft ? BodyPart.LEFT_ELBOW : BodyPart.RIGHT_ELBOW,
        UPPER_ARM_RADIUS, ELBOW_RADIUS);
    limb(body, elbow, wrist,
        anatomicalLeft ? BodyPart.LEFT_FOREARM : BodyPart.RIGHT_FOREARM,
        anatomicalLeft ? BodyPart.LEFT_HAND : BodyPart.RIGHT_HAND,
        FOREARM_RADIUS, HAND_RADIUS);
  }

  private void emitLeg(PosedHumanSkeleton body, boolean anatomicalLeft) {
    int hip = anatomicalLeft ? MetricSkeleton.LEFT_HIP : MetricSkeleton.RIGHT_HIP;
    int knee = anatomicalLeft ? MetricSkeleton.LEFT_KNEE : MetricSkeleton.RIGHT_KNEE;
    int ankle = anatomicalLeft ? MetricSkeleton.LEFT_ANKLE : MetricSkeleton.RIGHT_ANKLE;
    zoneAt(body, hip, HIP_RADIUS, anatomicalLeft ? BodyPart.LEFT_HIP : BodyPart.RIGHT_HIP);
    limb(body, hip, knee,
        anatomicalLeft ? BodyPart.LEFT_THIGH : BodyPart.RIGHT_THIGH,
        anatomicalLeft ? BodyPart.LEFT_KNEE : BodyPart.RIGHT_KNEE,
        THIGH_RADIUS, KNEE_RADIUS);
    limb(body, knee, ankle,
        anatomicalLeft ? BodyPart.LEFT_SHANK : BodyPart.RIGHT_SHANK,
        anatomicalLeft ? BodyPart.LEFT_FOOT : BodyPart.RIGHT_FOOT,
        SHANK_RADIUS, FOOT_RADIUS);
  }

  /**
   * Emite o eixo completo de um membro mais a zona articular centrada na junta da ponta.
   *
   * <p>Duas decisões que custaram acurácia até serem corrigidas.
   *
   * <p><b>O eixo vai até a junta, sem encurtar.</b> Uma versão anterior parava o eixo antes da ponta
   * para "não sobrepor" os rótulos, e isso abria um buraco de alguns centímetros no meio do membro: o
   * corpo de treino tinha um vão que nenhum corpo real tem, e o classificador aprendia a fronteira
   * errada. Sobreposição não é problema porque o renderizador escolhe a cápsula mais próxima do raio,
   * não a última inserida — o resultado é geometricamente determinado.
   *
   * <p><b>A zona articular é simétrica em torno da junta e um pouco mais gorda que o eixo.</b>
   * Simétrica porque um centróide deslocado enviesa o modo do mean shift por construção; mais gorda
   * porque é assim que ela ganha o raio na vizinhança da junta. Também é anatomia: cotovelo, joelho,
   * punho fechado e tornozelo são de fato mais grossos que o segmento vizinho.
   */
  private void limb(
      PosedHumanSkeleton body,
      int fromJoint,
      int toJoint,
      int shaftPart,
      int jointPart,
      float shaftRadius,
      float jointRadius) {
    float fromX = body.x(fromJoint);
    float fromY = body.y(fromJoint);
    float fromZ = body.z(fromJoint);
    float toX = body.x(toJoint);
    float toY = body.y(toJoint);
    float toZ = body.z(toJoint);
    float dx = toX - fromX;
    float dy = toY - fromY;
    float dz = toZ - fromZ;
    float length = (float) Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
    if (length < 1e-4f) {
      return;
    }
    capsule(fromX, fromY, fromZ, toX, toY, toZ, shaftRadius, shaftPart);
    float half = zoneLength * 0.5f / length;
    capsule(
        toX - (dx * half), toY - (dy * half), toZ - (dz * half),
        toX + (dx * half), toY + (dy * half), toZ + (dz * half),
        jointRadius, jointPart);
  }

  /** Zona articular isolada, para juntas que não são ponta de um eixo: cabeça, ombro e quadril. */
  private void zoneAt(PosedHumanSkeleton body, int joint, float radius, int part) {
    float half = zoneLength * 0.25f;
    capsule(body.x(joint), body.y(joint) - half, body.z(joint),
        body.x(joint), body.y(joint) + half, body.z(joint), radius, part);
  }

  /** Cápsula entre duas juntas, encurtada pelo fator informado em relação ao centro. */
  private void capsuleBetween(
      PosedHumanSkeleton body, int firstJoint, int secondJoint, float radius, int part,
      float shrink) {
    float centerX = mid(body, firstJoint, secondJoint, 0);
    float centerY = mid(body, firstJoint, secondJoint, 1);
    float centerZ = mid(body, firstJoint, secondJoint, 2);
    float dx = (body.x(firstJoint) - centerX) * shrink;
    float dy = (body.y(firstJoint) - centerY) * shrink;
    float dz = (body.z(firstJoint) - centerZ) * shrink;
    capsule(centerX + dx, centerY + dy, centerZ + dz,
        centerX - dx, centerY - dy, centerZ - dz, radius, part);
  }

  private static float mid(PosedHumanSkeleton body, int firstJoint, int secondJoint, int axis) {
    if (axis == 0) {
      return (body.x(firstJoint) + body.x(secondJoint)) * 0.5f;
    }
    if (axis == 1) {
      return (body.y(firstJoint) + body.y(secondJoint)) * 0.5f;
    }
    return (body.z(firstJoint) + body.z(secondJoint)) * 0.5f;
  }

  private void capsule(
      float x1, float y1, float z1, float x2, float y2, float z2, float radius, int part) {
    renderer.addWorldCapsule(x1, y1, z1, x2, y2, z2, radius * scale, part);
  }
}
