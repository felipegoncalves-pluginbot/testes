package com.felipe.elftemplate.tracking3d;

import com.felipe.elftemplate.tracking.TrackingResult;

/**
 * Projeta o esqueleto métrico de volta para o contrato de {@link TrackingResult}.
 *
 * <p>É o único lugar do novo pipeline que fala em coordenadas normalizadas de quadro, e o único que
 * traduz lateralidade. Assim as Activities, os jogos, o overlay e o feedback do robô continuam
 * funcionando sem alteração, e qualquer dúvida sobre espelhamento tem um endereço só.
 *
 * <p><b>Tradução de lados.</b> No novo motor, {@code LEFT_*} é o lado esquerdo da pessoa. No contrato
 * antigo, {@code leftHand} é o lado esquerdo <em>da imagem</em> (é o que `MirrorGameEngine` e
 * `COORDINATE_FRAMES.md` assumem para acionar a asa esquerda). Como o jogador está de frente, o lado
 * esquerdo da imagem corresponde à mão direita dele. Portanto:
 *
 * <pre>
 *   TrackingResult.left*  ← MetricSkeleton.RIGHT_*   (anatomicamente direito, aparece à esquerda)
 *   TrackingResult.right* ← MetricSkeleton.LEFT_*
 * </pre>
 *
 * <p>Não existe nenhum {@code 1.0f - x} aqui: a inversão de lado é feita só por essa troca de
 * índices, e o X normalizado sai direto da projeção pinhole.
 */
public final class TrackingResultAdapter {

  private TrackingResultAdapter() {}

  /** Preenche o resultado legado a partir do esqueleto métrico e do plano do chão. */
  public static void apply(
      MetricSkeleton skeleton,
      GroundPlane plane,
      AstraIntrinsics intrinsics,
      TrackingResult out) {
    out.reset();
    if (skeleton == null || !skeleton.valid) {
      return;
    }
    out.isPlayerPresent = true;
    out.hasFeetInFrame = skeleton.feetVisible;
    out.hasLegsInFrame = skeleton.feetVisible || !skeleton.seated;
    out.playerDistanceZ = Math.round(skeleton.torsoDepthM * 1000f);
    writeDiagnostics(skeleton, out);

    writeJoint(skeleton, MetricSkeleton.HEAD, plane, intrinsics, out.head);
    writeJoint(skeleton, MetricSkeleton.NECK, plane, intrinsics, out.neck);
    writeJoint(skeleton, MetricSkeleton.SPINE, plane, intrinsics, out.spine);
    writeJoint(skeleton, MetricSkeleton.RIGHT_SHOULDER, plane, intrinsics, out.leftShoulder);
    writeJoint(skeleton, MetricSkeleton.LEFT_SHOULDER, plane, intrinsics, out.rightShoulder);
    writeJoint(skeleton, MetricSkeleton.RIGHT_ELBOW, plane, intrinsics, out.leftElbow);
    writeJoint(skeleton, MetricSkeleton.LEFT_ELBOW, plane, intrinsics, out.rightElbow);
    writeJoint(skeleton, MetricSkeleton.RIGHT_WRIST, plane, intrinsics, out.leftHand);
    writeJoint(skeleton, MetricSkeleton.LEFT_WRIST, plane, intrinsics, out.rightHand);
    writeJoint(skeleton, MetricSkeleton.RIGHT_HIP, plane, intrinsics, out.leftHip);
    writeJoint(skeleton, MetricSkeleton.LEFT_HIP, plane, intrinsics, out.rightHip);
    writeJoint(skeleton, MetricSkeleton.RIGHT_KNEE, plane, intrinsics, out.leftKnee);
    writeJoint(skeleton, MetricSkeleton.LEFT_KNEE, plane, intrinsics, out.rightKnee);
    writeJoint(skeleton, MetricSkeleton.RIGHT_ANKLE, plane, intrinsics, out.leftFoot);
    writeJoint(skeleton, MetricSkeleton.LEFT_ANKLE, plane, intrinsics, out.rightFoot);

    out.leftHandX = out.leftHand.x;
    out.leftHandY = out.leftHand.y;
    out.rightHandX = out.rightHand.x;
    out.rightHandY = out.rightHand.y;
    out.playerCentroidX = out.spine.x;
    out.playerCentroidY = out.spine.y;
  }

  /**
   * Publica o que o esqueleto sabe e o contrato antigo expressa como diagnóstico.
   *
   * <p>A confiança do punho é o dado que faltava no pipeline de blob: lá um braço oculto produzia
   * uma mão sintética indistinguível de uma medida, e o overlay e as asas reagiam a ela. Aqui o
   * consumidor recebe o número e decide.
   *
   * <p>{@code isPoseFusionActive} fica falso de propósito: não existe fusão com a câmera RGB neste
   * caminho, e é esse sinalizador que faz {@code MirrorGameEngine} escolher o mapeamento de yaw.
   */
  private static void writeDiagnostics(MetricSkeleton skeleton, TrackingResult out) {
    out.diagnostics.isSeatedPose = skeleton.seated;
    out.diagnostics.isPoseFusionActive = false;
    // Troca de lados igual à das juntas: o punho anatômico direito aparece à esquerda da imagem.
    out.diagnostics.leftArmConfidence = skeleton.confidence(MetricSkeleton.RIGHT_WRIST);
    out.diagnostics.rightArmConfidence = skeleton.confidence(MetricSkeleton.LEFT_WRIST);
  }

  /**
   * Converte um joint do mundo métrico para o quadro normalizado.
   *
   * <p>Volta ao referencial óptico pelo plano do chão e projeta pelos intrínsecos reais. O Z de cada
   * junta é o Z próprio dela, não a distância média do corpo como no pipeline antigo — o que permite
   * distinguir uma mão estendida à frente do tronco.
   */
  private static void writeJoint(
      MetricSkeleton skeleton,
      int joint,
      GroundPlane plane,
      AstraIntrinsics intrinsics,
      com.felipe.elftemplate.tracking.Joint out) {
    float worldY = skeleton.y(joint);
    float worldZ = skeleton.z(joint);
    float camY = plane.cameraYFromWorld(worldY, worldZ);
    float camZ = plane.cameraZFromWorld(worldY, worldZ);
    if (camZ <= 0.05f) {
      camZ = 0.05f;
    }
    float normX = clamp01(intrinsics.projectNormX(skeleton.x(joint), camZ));
    float normY = clamp01(intrinsics.projectNormY(camY, camZ));
    out.set(normX, normY, Math.round(camZ * 1000f));
  }

  private static float clamp01(float value) {
    if (value < 0f) {
      return 0f;
    }
    return value > 1f ? 1f : value;
  }
}
