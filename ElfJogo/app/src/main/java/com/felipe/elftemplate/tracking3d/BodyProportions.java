package com.felipe.elftemplate.tracking3d;

/**
 * Proporções corporais como fração da estatura, na convenção clássica de Drillis &amp; Contini.
 *
 * <p>Fonte única de verdade para toda a geometria do esqueleto. O pipeline antigo espalhava números
 * como {@code neck = head + 0.12 * bodyH} e {@code shoulderSpan = 0.35 * bodyW / width} por vários
 * arquivos; como eram frações do quadro, a mesma pessoa mudava de proporção ao andar para frente ou
 * para trás. Aqui as frações são da estatura em metros, então valem em qualquer distância.
 *
 * <p>Alturas são medidas a partir do piso; comprimentos são distâncias entre centros de articulação.
 */
public final class BodyProportions {

  /** Altura do centro da cabeça. */
  public static final float HEAD_CENTER_HEIGHT = 0.936f;

  /** Altura da base do pescoço / acrômio (ombro). */
  public static final float SHOULDER_HEIGHT = 0.818f;

  /** Altura do centro do cotovelo. */
  public static final float ELBOW_HEIGHT = 0.630f;

  /** Altura do punho com o braço relaxado ao lado do corpo. */
  public static final float WRIST_HEIGHT = 0.485f;

  /** Altura do centro articular do quadril. */
  public static final float HIP_HEIGHT = 0.530f;

  /** Altura do centro do joelho. */
  public static final float KNEE_HEIGHT = 0.285f;

  /** Altura do tornozelo. */
  public static final float ANKLE_HEIGHT = 0.039f;

  /** Altura do meio do tronco (referência para o seed do campo geodésico). */
  public static final float TORSO_CENTER_HEIGHT = 0.680f;

  /** Meia distância entre os centros dos ombros (biacromial / 2). */
  public static final float SHOULDER_HALF_SPAN = 0.122f;

  /** Meia distância entre os centros dos quadris. */
  public static final float HIP_HALF_SPAN = 0.095f;

  /** Comprimento ombro-cotovelo (braço). */
  public static final float UPPER_ARM_LENGTH = 0.186f;

  /** Comprimento cotovelo-punho (antebraço). */
  public static final float FOREARM_LENGTH = 0.146f;

  /** Comprimento ombro-punho com o braço estendido. */
  public static final float ARM_LENGTH = UPPER_ARM_LENGTH + FOREARM_LENGTH;

  /** Comprimento quadril-joelho (coxa). */
  public static final float THIGH_LENGTH = 0.245f;

  /** Comprimento joelho-tornozelo (perna). */
  public static final float SHANK_LENGTH = 0.246f;

  /** Raio aproximado da cabeça, usado para janelas de busca. */
  public static final float HEAD_RADIUS = 0.055f;

  private BodyProportions() {}

  /** Converte uma fração de estatura em metros. */
  public static float meters(float ratio, float statureMeters) {
    return ratio * statureMeters;
  }

  /**
   * Distância geodésica esperada do meio do tronco até o punho, em metros.
   *
   * <p>Sobe pelo tronco até o ombro e desce pelo braço. Serve como expectativa para classificar
   * extremos geodésicos como mão em vez de cabeça ou pé.
   */
  public static float torsoToWristGeodesic(float statureMeters) {
    float torsoRun = (SHOULDER_HEIGHT - TORSO_CENTER_HEIGHT) + SHOULDER_HALF_SPAN;
    return (torsoRun + ARM_LENGTH) * statureMeters;
  }

  /** Distância geodésica esperada do meio do tronco até o topo da cabeça. */
  public static float torsoToHeadGeodesic(float statureMeters) {
    return (1.0f - TORSO_CENTER_HEIGHT) * statureMeters;
  }

  /** Distância geodésica esperada do meio do tronco até o tornozelo. */
  public static float torsoToAnkleGeodesic(float statureMeters) {
    return (TORSO_CENTER_HEIGHT - ANKLE_HEIGHT) * statureMeters;
  }
}
