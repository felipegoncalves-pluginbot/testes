package com.felipe.elftemplate.tracking3d;

/**
 * Esqueleto em metros no referencial do chão.
 *
 * <p>Eixos: X lateral (cresce para a direita da câmera), Y altura acima do piso, Z distância
 * horizontal. Nada aqui é normalizado pelo tamanho do quadro.
 *
 * <p><b>Lateralidade é anatômica.</b> {@code LEFT_*} é a mão esquerda <em>da pessoa</em>. Como o
 * jogador fica de frente para a câmera, o lado esquerdo dele aparece no lado direito da imagem, ou
 * seja com X maior. Essa é a correção do problema de nomenclatura do pipeline antigo, onde
 * "leftHand" significava lado esquerdo da imagem em `DepthBlobAnatomy`, mão direita anatômica em
 * `PoseDepthFusion` e asa esquerda do robô em `MirrorGameEngine`. A tradução para o contrato antigo
 * acontece em um único lugar, {@link TrackingResultAdapter}.
 *
 * <p>Layout SoA com índices constantes: copiar, filtrar e interpolar viram laços simples sem
 * alocação.
 */
public final class MetricSkeleton {

  public static final int HEAD = 0;
  public static final int NECK = 1;
  public static final int SPINE = 2;
  public static final int LEFT_SHOULDER = 3;
  public static final int RIGHT_SHOULDER = 4;
  public static final int LEFT_ELBOW = 5;
  public static final int RIGHT_ELBOW = 6;
  public static final int LEFT_WRIST = 7;
  public static final int RIGHT_WRIST = 8;
  public static final int LEFT_HIP = 9;
  public static final int RIGHT_HIP = 10;
  public static final int LEFT_KNEE = 11;
  public static final int RIGHT_KNEE = 12;
  public static final int LEFT_ANKLE = 13;
  public static final int RIGHT_ANKLE = 14;
  public static final int JOINT_COUNT = 15;

  private final float[] posX = new float[JOINT_COUNT];
  private final float[] posY = new float[JOINT_COUNT];
  private final float[] posZ = new float[JOINT_COUNT];
  private final float[] confidence = new float[JOINT_COUNT];

  /** Identidade estável da pessoa entre frames. */
  public int trackId = -1;

  public boolean valid;

  /** Estatura estimada em metros. */
  public float statureM;

  /** Distância horizontal do tronco, em metros. */
  public float torsoDepthM;

  /** Meia envergadura de ombros usada nesta estimativa, em metros. */
  public float shoulderHalfSpanM;

  public boolean seated;
  public boolean feetVisible;
  public boolean headOutOfFrame;

  /** Timestamp da medição, em milissegundos monotônicos. */
  public long timestampMs;

  public void reset() {
    valid = false;
    statureM = 0f;
    torsoDepthM = 0f;
    shoulderHalfSpanM = 0f;
    seated = false;
    feetVisible = false;
    headOutOfFrame = false;
    for (int j = 0; j < JOINT_COUNT; j++) {
      posX[j] = 0f;
      posY[j] = 0f;
      posZ[j] = 0f;
      confidence[j] = 0f;
    }
  }

  public void set(int joint, float x, float y, float z, float jointConfidence) {
    posX[joint] = x;
    posY[joint] = y;
    posZ[joint] = z;
    confidence[joint] = jointConfidence;
  }

  public float x(int joint) {
    return posX[joint];
  }

  public float y(int joint) {
    return posY[joint];
  }

  public float z(int joint) {
    return posZ[joint];
  }

  public float confidence(int joint) {
    return confidence[joint];
  }

  /** Distância 3D entre dois joints, em metros. */
  public float distance(int jointA, int jointB) {
    float dx = posX[jointA] - posX[jointB];
    float dy = posY[jointA] - posY[jointB];
    float dz = posZ[jointA] - posZ[jointB];
    return (float) Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
  }

  /** Cópia completa; usada pelo filtro temporal e pela publicação do resultado. */
  public void copyFrom(MetricSkeleton src) {
    if (src == null) {
      return;
    }
    trackId = src.trackId;
    valid = src.valid;
    statureM = src.statureM;
    torsoDepthM = src.torsoDepthM;
    shoulderHalfSpanM = src.shoulderHalfSpanM;
    seated = src.seated;
    feetVisible = src.feetVisible;
    headOutOfFrame = src.headOutOfFrame;
    timestampMs = src.timestampMs;
    for (int j = 0; j < JOINT_COUNT; j++) {
      posX[j] = src.posX[j];
      posY[j] = src.posY[j];
      posZ[j] = src.posZ[j];
      confidence[j] = src.confidence[j];
    }
  }
}
