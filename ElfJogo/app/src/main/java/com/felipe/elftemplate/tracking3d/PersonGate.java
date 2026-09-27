package com.felipe.elftemplate.tracking3d;

/**
 * Decide se um aglomerado 3D é uma pessoa, usando apenas grandezas em metros.
 *
 * <p>Todos os limites aqui são antropometria real, então valem igual a 0,8 m e a 4 m da câmera. O
 * filtro antigo (`DepthBlobClusterer.passesAnthropomorphicFilter`) usava fração de quadro e contagem
 * fixa de pixels, o que produzia dois erros opostos: descartava pessoas distantes (poucos pixels) e
 * aceitava móveis próximos (muitos pixels).
 */
public final class PersonGate {

  /** Estatura mínima aceita em pé; cobre criança pequena. */
  public static final float MIN_STANDING_HEIGHT_M = 0.80f;

  /** Estatura máxima plausível, com folga sobre o recorde humano. */
  public static final float MAX_STANDING_HEIGHT_M = 2.30f;

  /** Envergadura máxima: em T-pose a extensão de braços iguala a estatura. */
  public static final float MAX_SPAN_M = 2.10f;

  /** Espessura frontal máxima; acima disso é parede em diagonal ou dois corpos fundidos. */
  public static final float MAX_THICKNESS_M = 1.10f;

  /** Largura máxima do "pescoço": a faixa mais estreita acima da cintura. */
  public static final float MAX_NECK_BAND_M = 0.45f;

  /**
   * Largura máxima da faixa mais estreita quando a cabeça está fora do quadro.
   *
   * <p>Sem a cabeça no quadro não existe pescoço para medir: a faixa mais estreita visível é peito
   * ou cintura com os braços ao lado, 0,45 a 0,60 m num adulto. Exigir os 0,45 m do pescoço
   * reprovava justamente o jogador na distância de jogo. Com o Astra a menos de 0,9 m do chão (o
   * Sanbot Elf tem 0,90 m de altura), a cabeça de um adulto só entra no quadro a partir de ~2,3 m.
   */
  public static final float MAX_TORSO_BAND_HEAD_CUT_M = 0.70f;

  /** Piso absoluto de pontos, para o caso de grades muito decimadas. */
  private static final int ABSOLUTE_MIN_POINTS = 40;

  /** Fração da área esperada de um tronco que precisa estar preenchida. */
  private static final float TORSO_FILL_RATIO = 0.30f;

  private static final float TORSO_WIDTH_M = 0.40f;
  private static final float TORSO_HEIGHT_M = 0.60f;

  private PersonGate() {}

  /**
   * Número mínimo de células para aceitar um corpo na distância informada.
   *
   * <p>A densidade de pontos cai com 1/Z², então o limiar tem que subir na mesma proporção quando a
   * pessoa se aproxima e cair quando ela se afasta. É esse escalonamento que faltava no pipeline
   * antigo, onde o limiar fixo de 25/35 pixels era simultaneamente permissivo demais de perto e
   * restritivo demais de longe.
   */
  public static int minPointsAt(float depthMeters, DepthPointCloud cloud) {
    float cell = cloud.cellSizeMetersAt(Math.max(0.4f, depthMeters));
    if (cell <= 0f) {
      return ABSOLUTE_MIN_POINTS;
    }
    float expectedTorsoCells = (TORSO_WIDTH_M / cell) * (TORSO_HEIGHT_M / cell);
    int scaled = (int) (expectedTorsoCells * TORSO_FILL_RATIO);
    return Math.max(ABSOLUTE_MIN_POINTS, scaled);
  }

  /** Códigos de rejeição, expostos no HUD para dizer qual critério reprovou o aglomerado. */
  public static final int ACCEPTED = 0;

  public static final int REJECT_TOO_FEW_POINTS = 1;
  public static final int REJECT_TOO_WIDE = 2;
  public static final int REJECT_TOO_THICK = 3;
  public static final int REJECT_NO_NECK = 4;
  public static final int REJECT_STATURE = 5;

  /**
   * Aplica todos os critérios métricos ao aglomerado.
   *
   * @param floorMeasured se o plano do chão veio de medição real; quando falso, a altura absoluta não
   *     é confiável e o critério de estatura passa a usar a extensão vertical do corpo.
   * @return {@link #ACCEPTED} ou o código do critério que reprovou.
   */
  public static int evaluate(BodyCluster cluster, DepthPointCloud cloud, boolean floorMeasured) {
    if (cluster == null || cluster.pointCount <= 0) {
      return REJECT_TOO_FEW_POINTS;
    }
    if (cluster.pointCount < minPointsAt(cluster.nearestDepthM, cloud)) {
      return REJECT_TOO_FEW_POINTS;
    }
    if (cluster.widthM > MAX_SPAN_M) {
      return REJECT_TOO_WIDE;
    }
    if (cluster.thicknessM > MAX_THICKNESS_M) {
      return REJECT_TOO_THICK;
    }
    float maxBand = cluster.headOutOfFrame ? MAX_TORSO_BAND_HEAD_CUT_M : MAX_NECK_BAND_M;
    if (cluster.narrowestUpperBandM > maxBand) {
      return REJECT_NO_NECK;
    }
    return hasPlausibleStature(cluster, floorMeasured) ? ACCEPTED : REJECT_STATURE;
  }

  /** Texto curto para o HUD do robô. */
  public static String describe(int reason) {
    switch (reason) {
      case ACCEPTED:
        return "ok";
      case REJECT_TOO_FEW_POINTS:
        return "poucos pontos";
      case REJECT_TOO_WIDE:
        return "largo demais";
      case REJECT_TOO_THICK:
        return "espesso demais";
      case REJECT_NO_NECK:
        return "sem pescoco";
      case REJECT_STATURE:
        return "estatura fora da faixa";
      default:
        return "desconhecido";
    }
  }

  /**
   * Valida a estatura observada.
   *
   * <p>Dois modos, e a distinção importa no robô: com o piso medido, vale a estatura absoluta. Sem o
   * piso medido — sala sem chão visível no quadro, ou ajuste recusado por implausível — a altura
   * absoluta é apenas um palpite e usá-la como gate rejeitaria pessoas reais. Nesse caso o critério
   * passa a ser a extensão vertical do próprio corpo, que não depende de onde está o zero.
   */
  private static boolean hasPlausibleStature(BodyCluster cluster, boolean floorMeasured) {
    float verticalExtent = cluster.maxWorldY - cluster.minWorldY;
    if (cluster.headOutOfFrame || !floorMeasured) {
      return verticalExtent >= 0.45f && verticalExtent <= MAX_STANDING_HEIGHT_M;
    }
    return cluster.topHeightM >= MIN_STANDING_HEIGHT_M
        && cluster.topHeightM <= MAX_STANDING_HEIGHT_M;
  }

  /**
   * Estatura usada para derivar proporções do esqueleto.
   *
   * <p>Se a cabeça está no quadro, usa a estatura observada. Caso contrário assume uma estatura
   * adulta média, porque é melhor errar a escala do que produzir um esqueleto degenerado.
   */
  public static float statureForProportions(BodyCluster cluster) {
    if (cluster == null) {
      return 1.70f;
    }
    if (cluster.headOutOfFrame || cluster.topHeightM < MIN_STANDING_HEIGHT_M) {
      return 1.70f;
    }
    if (cluster.seated) {
      // Sentado: a estatura observada é ~0,78 da estatura em pé (altura sentado + comprimento da coxa).
      return Math.min(MAX_STANDING_HEIGHT_M, cluster.topHeightM / 0.78f);
    }
    return cluster.topHeightM;
  }
}
