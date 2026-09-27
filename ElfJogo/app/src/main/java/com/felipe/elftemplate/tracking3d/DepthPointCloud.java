package com.felipe.elftemplate.tracking3d;

import java.util.Arrays;

/**
 * Nuvem de pontos métrica em layout SoA (structure of arrays), reaproveitada entre frames.
 *
 * <p>Converte o mapa de profundidade 16-bit do Astra em coordenadas 3D em metros uma única vez por
 * frame. Todo o resto do pipeline consome esta nuvem, então nenhuma etapa posterior precisa mexer em
 * pixels ou repetir deprojeção.
 *
 * <p>Decisões de performance (RK3288 / Android 6, orçamento de 25 ms por frame):
 *
 * <ul>
 *   <li>Decimação para uma grade alvo (~160x120 = 19.2k pontos) em vez dos 307k pixels originais.
 *   <li>Arrays primitivos pré-alocados; zero alocação no hot path após o primeiro frame.
 *   <li>Filtro de mediana em 5 taps por célula, que remove o speckle e os buracos característicos
 *       do sensor estruturado sem o custo de uma passada separada de suavização.
 * </ul>
 *
 * <p>Não é thread-safe por desenho: existe um único worker de depth.
 */
public final class DepthPointCloud {

  /**
   * Largura alvo da grade decimada.
   *
   * <p>128 dá 128x96 = 12.288 células, 36% menos que 160x120. Medido no RK3288, o custo do pipeline é
   * praticamente linear no número de células, e a perda de detalhe é aceitável: a 3 m uma célula mede
   * 2,6 cm, então um antebraço de 9 cm ainda cobre três células.
   */
  public static final int DEFAULT_TARGET_GRID_WIDTH = 128;

  /** Abaixo disso o Astra devolve lixo (limite físico do sensor estruturado é ~0,6 m). */
  public static final int DEFAULT_MIN_DEPTH_MM = 400;

  /**
   * Teto de 4,5 m. O pipeline antigo cortava em 2.200 mm, o que fazia o jogador simplesmente
   * desaparecer ao dar dois passos para trás.
   */
  public static final int DEFAULT_MAX_DEPTH_MM = 4500;

  private final AstraIntrinsics intrinsics = new AstraIntrinsics();
  private final short[] medianTaps = new short[3];

  private int srcWidth;
  private int srcHeight;
  private int decimation = 1;
  private int gridWidth;
  private int gridHeight;
  private int cellCount;
  private int validCount;

  private int minDepthMm = DEFAULT_MIN_DEPTH_MM;
  private int maxDepthMm = DEFAULT_MAX_DEPTH_MM;

  private float[] camX = new float[0];
  private float[] camY = new float[0];
  private float[] camZ = new float[0];

  /** Distância média das células válidas do frame, usada para dimensionar limiares. */
  private float meanDepthMeters;

  public void setDepthRangeMm(int minMm, int maxMm) {
    this.minDepthMm = Math.max(1, minMm);
    this.maxDepthMm = Math.max(this.minDepthMm + 1, maxMm);
  }

  /**
   * Prepara buffers para uma resolução de origem.
   *
   * <p>Idempotente: chamar a cada frame não realoca nada se a resolução não mudou.
   */
  public void configure(int frameWidth, int frameHeight, int targetGridWidth) {
    if (frameWidth <= 0 || frameHeight <= 0) {
      return;
    }
    int decim = Math.max(1, Math.round(frameWidth / (float) Math.max(1, targetGridWidth)));
    if (frameWidth == srcWidth && frameHeight == srcHeight && decim == decimation) {
      return;
    }
    this.srcWidth = frameWidth;
    this.srcHeight = frameHeight;
    this.decimation = decim;
    this.gridWidth = Math.max(1, frameWidth / decim);
    this.gridHeight = Math.max(1, frameHeight / decim);
    this.cellCount = gridWidth * gridHeight;
    if (camZ.length < cellCount) {
      camX = new float[cellCount];
      camY = new float[cellCount];
      camZ = new float[cellCount];
    }
    // Os intrínsecos são os da grade decimada: fx escala junto com a largura.
    intrinsics.configure(gridWidth, gridHeight);
  }

  /**
   * Deprojeta o frame de profundidade inteiro para metros.
   *
   * @return quantidade de células válidas.
   */
  public int build(short[] depthData, int frameWidth, int frameHeight) {
    configure(frameWidth, frameHeight, DEFAULT_TARGET_GRID_WIDTH);
    validCount = 0;
    if (depthData == null || depthData.length < frameWidth * frameHeight) {
      Arrays.fill(camZ, 0, cellCount, 0f);
      return 0;
    }
    long depthSumMm = 0;
    for (int gy = 0; gy < gridHeight; gy++) {
      int rowBase = gy * gridWidth;
      int srcV = gy * decimation;
      for (int gx = 0; gx < gridWidth; gx++) {
        int idx = rowBase + gx;
        int depthMm = sampleMedianDepth(depthData, gx * decimation, srcV);
        if (depthMm <= 0) {
          camZ[idx] = 0f;
          continue;
        }
        float zMeters = depthMm * 0.001f;
        camZ[idx] = zMeters;
        camX[idx] = intrinsics.deprojectX(gx, zMeters);
        camY[idx] = intrinsics.deprojectY(gy, zMeters);
        validCount++;
        depthSumMm += depthMm;
      }
    }
    meanDepthMeters = validCount > 0 ? (depthSumMm / (float) validCount) * 0.001f : 0f;
    return validCount;
  }

  /**
   * Mediana das amostras válidas em uma cruz de 5 taps dentro do bloco de decimação.
   *
   * <p>Retorna 0 quando nenhuma amostra cai na faixa útil, o que marca a célula como inválida.
   */
  private int sampleMedianDepth(short[] depthData, int srcU, int srcV) {
    int taps = 0;
    int half = Math.max(1, decimation >> 1);
    taps = appendTap(depthData, srcU, srcV, taps);
    taps = appendTap(depthData, srcU + half, srcV, taps);
    taps = appendTap(depthData, srcU, srcV + half, taps);
    if (taps == 0) {
      return 0;
    }
    if (taps == 1) {
      return medianTaps[0] & 0xFFFF;
    }
    if (taps == 2) {
      return (medianTaps[0] + medianTaps[1]) >> 1;
    }
    return medianOfThree(medianTaps[0], medianTaps[1], medianTaps[2]);
  }

  /**
   * Mediana de três amostras sem ordenar.
   *
   * <p>Três taps em vez de cinco: remove o speckle igual (que é ruído isolado) por uma fração do
   * custo, e esta é a passada mais executada do pipeline — uma vez por célula da grade, todo frame.
   */
  private static int medianOfThree(short a, short b, short c) {
    int first = a & 0xFFFF;
    int second = b & 0xFFFF;
    int third = c & 0xFFFF;
    if (first > second) {
      int swap = first;
      first = second;
      second = swap;
    }
    if (second > third) {
      second = third;
    }
    return Math.max(first, second);
  }

  private int appendTap(short[] depthData, int srcU, int srcV, int taps) {
    if (srcU < 0 || srcV < 0 || srcU >= srcWidth || srcV >= srcHeight) {
      return taps;
    }
    int raw = depthData[srcV * srcWidth + srcU] & 0xFFFF;
    if (raw < minDepthMm || raw > maxDepthMm) {
      return taps;
    }
    medianTaps[taps] = (short) raw;
    return taps + 1;
  }

  public AstraIntrinsics getIntrinsics() {
    return intrinsics;
  }

  public int getGridWidth() {
    return gridWidth;
  }

  public int getGridHeight() {
    return gridHeight;
  }

  public int getCellCount() {
    return cellCount;
  }

  public int getValidCount() {
    return validCount;
  }

  public int getDecimation() {
    return decimation;
  }

  public int getSourceWidth() {
    return srcWidth;
  }

  public int getSourceHeight() {
    return srcHeight;
  }

  public float getMeanDepthMeters() {
    return meanDepthMeters;
  }

  public boolean isValid(int index) {
    return index >= 0 && index < cellCount && camZ[index] > 0f;
  }

  public float x(int index) {
    return camX[index];
  }

  public float y(int index) {
    return camY[index];
  }

  public float z(int index) {
    return camZ[index];
  }

  public int indexOf(int gx, int gy) {
    return gy * gridWidth + gx;
  }

  public int gridX(int index) {
    return index % gridWidth;
  }

  public int gridY(int index) {
    return index / gridWidth;
  }

  /**
   * Distância métrica 3D entre duas células.
   *
   * <p>Base do critério de conectividade da segmentação e do custo das arestas do grafo geodésico.
   */
  public float distance(int a, int b) {
    return (float) Math.sqrt(squaredDistance(a, b));
  }

  /**
   * Distância 3D ao quadrado entre duas células.
   *
   * <p>Para comparar contra um limiar não é preciso extrair a raiz. Em laços que visitam milhares de
   * células por frame no RK3288, essa raiz evitada é medível.
   */
  public float squaredDistance(int a, int b) {
    float dx = camX[a] - camX[b];
    float dy = camY[a] - camY[b];
    float dz = camZ[a] - camZ[b];
    return (dx * dx) + (dy * dy) + (dz * dz);
  }

  /** Tamanho métrico aproximado de uma célula da grade na distância informada. */
  public float cellSizeMetersAt(float depthMeters) {
    float pixelsPerMeter = intrinsics.pixelsPerMeterAt(depthMeters);
    if (pixelsPerMeter <= 0f) {
      return 0.01f;
    }
    return 1.0f / pixelsPerMeter;
  }
}
