package com.felipe.elftemplate.tracking3d;

/**
 * Aglomerado 3D candidato a pessoa, descrito em metros no referencial do chão.
 *
 * <p>Instâncias vivem em um pool no segmentador e são reutilizadas entre frames: nada aqui é alocado
 * no hot path.
 *
 * <p>Contraste com o {@code PersonBlob} antigo, que guardava frações de quadro (`aspectHW`,
 * `fillDensity`, `minY/height`): aquelas grandezas mudavam com a distância, então o mesmo corpo
 * passava ou não passava no filtro dependendo de onde estava na sala.
 */
public final class BodyCluster {

  /** Rótulo do aglomerado na grade de labels do segmentador. */
  public int label;

  public int pointCount;

  /** Centróide no referencial do mundo (X lateral, Y altura acima do piso, Z distância). */
  public float centroidX;

  public float centroidY;
  public float centroidZ;

  /**
   * Caixa envolvente na grade decimada.
   *
   * <p>Toda varredura posterior é limitada a esta caixa. Sem isso, cada etapa do ajuste do esqueleto
   * percorria a grade inteira: eram dez passadas completas por frame, e no RK3288 o pipeline media
   * 504 ms por frame com a pessoa perto da câmera.
   */
  public int minGridX;

  public int maxGridX;
  public int minGridY;
  public int maxGridY;

  public float minWorldX;
  public float maxWorldX;
  public float minWorldY;
  public float maxWorldY;
  public float minWorldZ;
  public float maxWorldZ;

  /** Altura do ponto mais alto acima do piso, em metros: a estatura observada. */
  public float topHeightM;

  /** Largura lateral e espessura em profundidade, em metros. */
  public float widthM;

  public float thicknessM;

  /** Distância horizontal do ponto mais próximo, em metros. */
  public float nearestDepthM;

  public boolean touchesFrameTop;
  public boolean touchesFrameBottom;
  public boolean touchesFrameLeft;
  public boolean touchesFrameRight;

  /** Postura sentada inferida por estatura observada baixa com largura de tronco normal. */
  public boolean seated;

  /** Topo do corpo fora do quadro: a estatura observada não pode ser usada como gate. */
  public boolean headOutOfFrame;

  /**
   * Arestas da borda com vizinho válido fora do aglomerado; quantas dão para algo mais perto;
   * quantas continuam a mesma superfície além do alcance (ver {@link ClusterBoundary}).
   */
  public int boundaryEdges;

  public int occludedEdges;
  public int beyondRangeEdges;

  /**
   * Menor largura horizontal encontrada nas faixas de altura da metade superior do corpo, em metros.
   *
   * <p>É o discriminador que separa gente de mobília: uma pessoa sempre tem um pescoço, ou seja
   * alguma faixa estreita acima da cintura, mesmo com os braços erguidos. Sofá, parede e balcão não
   * têm nenhuma faixa estreita. E, ao contrário de `aspectHW`, a medida é em metros e não muda com a
   * distância.
   */
  public float narrowestUpperBandM;

  /**
   * X central da faixa mais estreita: o eixo vertical do corpo.
   *
   * <p>Melhor referência lateral que o centróide, porque o centróide é arrastado por um braço
   * estendido enquanto o pescoço fica onde está.
   */
  public float bodyAxisX;

  /** Altura do centro da faixa mais estreita (base do pescoço), em metros. */
  public float neckBandY;

  /** Altura de cada faixa do perfil horizontal, em metros. */
  static final float BAND_HEIGHT_M = 0.10f;

  /** Faixas suficientes para 2,4 m de altura. */
  static final int BAND_COUNT = 24;

  final float[] bandMinX = new float[BAND_COUNT];
  final float[] bandMaxX = new float[BAND_COUNT];
  final int[] bandCount = new int[BAND_COUNT];

  /**
   * Largura de cada coluna do perfil vertical, em metros.
   *
   * <p>3 cm mantém o erro de quantização do eixo do corpo em ~1,5 cm. Com 5 cm o eixo errava até
   * 2,5 cm, o que era suficiente para uma janela de busca da cabeça encostar no braço erguido.
   */
  static final float COLUMN_WIDTH_M = 0.03f;

  /** Meia largura do volume rastreado: o FOV horizontal do Astra a 4 m cobre ~±2,25 m. */
  static final float COLUMN_RANGE_M = 2.30f;

  static final int COLUMN_COUNT = (int) ((COLUMN_RANGE_M * 2f) / COLUMN_WIDTH_M) + 1;

  final int[] columnCount = new int[COLUMN_COUNT];

  public void reset() {
    label = 0;
    pointCount = 0;
    narrowestUpperBandM = 0f;
    bodyAxisX = 0f;
    neckBandY = 0f;
    for (int i = 0; i < BAND_COUNT; i++) {
      bandMinX[i] = Float.MAX_VALUE;
      bandMaxX[i] = -Float.MAX_VALUE;
      bandCount[i] = 0;
    }
    for (int i = 0; i < COLUMN_COUNT; i++) {
      columnCount[i] = 0;
    }
    centroidX = 0f;
    centroidY = 0f;
    centroidZ = 0f;
    minGridX = Integer.MAX_VALUE;
    maxGridX = -1;
    minGridY = Integer.MAX_VALUE;
    maxGridY = -1;
    minWorldX = Float.MAX_VALUE;
    maxWorldX = -Float.MAX_VALUE;
    minWorldY = Float.MAX_VALUE;
    maxWorldY = -Float.MAX_VALUE;
    minWorldZ = Float.MAX_VALUE;
    maxWorldZ = -Float.MAX_VALUE;
    topHeightM = 0f;
    widthM = 0f;
    thicknessM = 0f;
    nearestDepthM = 0f;
    touchesFrameTop = false;
    touchesFrameBottom = false;
    touchesFrameLeft = false;
    touchesFrameRight = false;
    seated = false;
    headOutOfFrame = false;
    boundaryEdges = 0;
    occludedEdges = 0;
    beyondRangeEdges = 0;
  }

  /** Acumula um ponto nos perfis horizontal (faixas) e vertical (colunas). */
  void addBandSample(float worldY, float worldX) {
    int column = (int) ((worldX + COLUMN_RANGE_M) / COLUMN_WIDTH_M);
    if (column >= 0 && column < COLUMN_COUNT) {
      columnCount[column]++;
    }
    int band = (int) (worldY / BAND_HEIGHT_M);
    if (band < 0 || band >= BAND_COUNT) {
      return;
    }
    if (worldX < bandMinX[band]) {
      bandMinX[band] = worldX;
    }
    if (worldX > bandMaxX[band]) {
      bandMaxX[band] = worldX;
    }
    bandCount[band]++;
  }

  /** Consolida as estatísticas derivadas depois de acumular todos os pontos. */
  void finish(int accumulatedCount, double sumX, double sumY, double sumZ) {
    pointCount = accumulatedCount;
    if (accumulatedCount <= 0) {
      return;
    }
    centroidX = (float) (sumX / accumulatedCount);
    centroidY = (float) (sumY / accumulatedCount);
    centroidZ = (float) (sumZ / accumulatedCount);
    topHeightM = maxWorldY;
    widthM = maxWorldX - minWorldX;
    thicknessM = maxWorldZ - minWorldZ;
    nearestDepthM = minWorldZ;
    headOutOfFrame = touchesFrameTop;
    seated = !headOutOfFrame && topHeightM < 1.45f && widthM > 0.25f;
    computeNarrowestUpperBand();
    bodyAxisX = computeBodyAxisX();
  }

  /**
   * Eixo vertical do corpo como mediana lateral do perfil de colunas.
   *
   * <p>A mediana é dominada por tronco e pernas, que juntos concentram a esmagadora maioria dos
   * pontos; um braço estendido pesa algumas dezenas de pontos contra mais de mil e desloca o eixo em
   * poucos milímetros.
   *
   * <p>Duas alternativas foram descartadas por quebrarem em pose real: a faixa horizontal mais
   * estreita vira o antebraço quando a mão sobe acima da cabeça, e a coluna mais densa (moda) escolhe
   * a coluna onde ombro e perna se sobrepõem, porque ombro e quadril ficam a poucos centímetros um do
   * outro em X.
   */
  private float computeBodyAxisX() {
    int total = 0;
    for (int column = 0; column < COLUMN_COUNT; column++) {
      total += columnCount[column];
    }
    if (total <= 0) {
      return centroidX;
    }
    int half = total / 2;
    int running = 0;
    for (int column = 0; column < COLUMN_COUNT; column++) {
      running += columnCount[column];
      if (running >= half) {
        return ((column + 0.5f) * COLUMN_WIDTH_M) - COLUMN_RANGE_M;
      }
    }
    return centroidX;
  }

  /**
   * Varre as faixas acima da metade da estatura e guarda a mais estreita com suporte real.
   *
   * <p>Faixas com poucos pontos são ignoradas para que um respingo de ruído no topo não vire um
   * "pescoço" artificial. Além da largura, o centro dessa faixa define o eixo do corpo.
   */
  private void computeNarrowestUpperBand() {
    int firstBand = (int) ((topHeightM * 0.5f) / BAND_HEIGHT_M);
    float narrowest = Float.MAX_VALUE;
    int narrowestBand = -1;
    for (int band = Math.max(0, firstBand); band < BAND_COUNT; band++) {
      if (bandCount[band] < 3) {
        continue;
      }
      float width = bandMaxX[band] - bandMinX[band];
      if (width < narrowest) {
        narrowest = width;
        narrowestBand = band;
      }
    }
    if (narrowestBand < 0) {
      narrowestUpperBandM = widthM;
      neckBandY = topHeightM * 0.8f;
      return;
    }
    narrowestUpperBandM = narrowest;
    neckBandY = (narrowestBand + 0.5f) * BAND_HEIGHT_M;
  }

  /** Distância horizontal (planta) entre centróides, usada na associação temporal. */
  public float groundDistanceTo(float otherX, float otherZ) {
    float dx = centroidX - otherX;
    float dz = centroidZ - otherZ;
    return (float) Math.sqrt((dx * dx) + (dz * dz));
  }
}
