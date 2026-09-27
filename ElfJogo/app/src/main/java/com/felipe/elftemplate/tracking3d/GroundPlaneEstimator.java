package com.felipe.elftemplate.tracking3d;

import java.util.Arrays;

/**
 * Estima pitch e altura do Astra a partir do próprio piso, com uma Hough 1D sobre o pitch.
 *
 * <p>Ideia: para um pitch candidato θ, a altura de cada ponto vira {@code a = -(Yc·cosθ + Zc·sinθ)}.
 * No θ correto, o piso — a maior superfície horizontal da cena — colapsa em um único bin do
 * histograma de alturas; em qualquer outro θ ele se espalha. Então basta varrer θ e escolher o que
 * produz o pico mais forte no cluster mais baixo. O valor desse bin é, por definição, menos a altura
 * da câmera.
 *
 * <p>Isso resolve três problemas do pipeline antigo de uma vez: ele assumia câmera nivelada, tratava
 * "chão" como as linhas abaixo de 90% do quadro (o que come os pés do jogador e deixa passar o piso
 * quando a câmera está inclinada) e não tinha nenhuma noção de altura real.
 *
 * <p>Custo: ~41 pitches x ~2k pontos subamostrados. Roda a cada N frames, não a cada frame.
 */
public final class GroundPlaneEstimator {

  private static final float PITCH_MIN_DEG = -20f;
  private static final float PITCH_MAX_DEG = 40f;
  private static final float PITCH_STEP_DEG = 1.5f;

  private static final float BIN_SIZE_M = 0.02f;
  private static final float RANGE_MIN_M = -3.0f;
  private static final float RANGE_MAX_M = 1.5f;
  private static final int BIN_COUNT = (int) ((RANGE_MAX_M - RANGE_MIN_M) / BIN_SIZE_M) + 1;

  /** Amostra 1 célula a cada 3 em cada eixo: 1/9 dos pontos, suficiente para um plano. */
  private static final int SAMPLE_STRIDE = 3;

  /**
   * Alturas de montagem fisicamente possíveis para o Astra no Sanbot Elf.
   *
   * <p>O robô inteiro mede 0,90 m (ficha técnica: 902 mm), então o sensor não pode estar acima
   * disso. A faixa antiga, de 0,75 a 1,45 m, era quase toda impossível e recusava as medições reais
   * do piso: no robô o estimador chegou a medir 0,68 m, que foi descartado como "tampo de mesa".
   * Tampo de mesa ou assento de cadeira ficam a menos de 0,45 m abaixo de um sensor dessa altura, e
   * são eles que o piso mínimo recusa. O teto de 1,10 m só deixa folga para os cenários sintéticos
   * dos testes.
   */
  private static final float MIN_PLAUSIBLE_HEIGHT_M = 0.45f;

  private static final float MAX_PLAUSIBLE_HEIGHT_M = 1.10f;

  /**
   * Inclinação plausível, em graus.
   *
   * <p>O Astra fica na cabeça do Elf, que inclina; por isso a faixa cobre quase toda a busca.
   */
  private static final float MIN_PLAUSIBLE_PITCH_DEG = -20f;

  private static final float MAX_PLAUSIBLE_PITCH_DEG = 35f;

  /** Suavização em direção à nova medida, para o referencial não pular entre frames. */
  private static final float BLEND_ALPHA = 0.25f;

  private final int[] histogram = new int[BIN_COUNT];

  private boolean lastFitSucceeded;
  private int lastFloorSupport;
  private float searchPitchRad;
  private float searchFloorValue;
  private int searchScore;

  /**
   * Ajusta o plano do chão à nuvem e atualiza {@code plane} quando o ajuste é confiável.
   *
   * @return true se o piso foi medido neste frame.
   */
  public boolean fit(DepthPointCloud cloud, GroundPlane plane) {
    lastFitSucceeded = false;
    lastFloorSupport = 0;
    if (cloud == null || plane == null || cloud.getValidCount() < 200) {
      return false;
    }
    if (!searchBestPitch(cloud)) {
      return false;
    }
    float refinedFloor = refineFloorValue(cloud, searchPitchRad, searchFloorValue);
    float cameraHeight = -refinedFloor;
    if (!isPlausibleMounting(cameraHeight, searchPitchRad)) {
      return false;
    }
    float alpha = plane.isMeasured() ? BLEND_ALPHA : 1.0f;
    plane.blendToward(searchPitchRad, cameraHeight, alpha);
    lastFitSucceeded = true;
    lastFloorSupport = searchScore;
    return true;
  }

  /** Varre os pitches candidatos e guarda o que produz o piso mais bem definido. */
  private boolean searchBestPitch(DepthPointCloud cloud) {
    int minSupport = Math.max(20, countSampled(cloud) / 40);
    searchPitchRad = 0f;
    searchFloorValue = 0f;
    searchScore = 0;
    for (float deg = PITCH_MIN_DEG; deg <= PITCH_MAX_DEG; deg += PITCH_STEP_DEG) {
      float pitchRad = (float) Math.toRadians(deg);
      fillHistogram(cloud, pitchRad);
      int floorBin = findFloorModeBin(minSupport);
      if (floorBin < 0) {
        continue;
      }
      int score = binScore(floorBin);
      if (score > searchScore) {
        searchScore = score;
        searchPitchRad = pitchRad;
        searchFloorValue = RANGE_MIN_M + (floorBin * BIN_SIZE_M);
      }
    }
    return searchScore > 0;
  }

  /** Recusa planos que a montagem física do robô não permite: mesa, cadeira, bancada. */
  private static boolean isPlausibleMounting(float cameraHeightM, float pitchRad) {
    if (cameraHeightM < MIN_PLAUSIBLE_HEIGHT_M || cameraHeightM > MAX_PLAUSIBLE_HEIGHT_M) {
      return false;
    }
    float pitchDeg = (float) Math.toDegrees(pitchRad);
    return pitchDeg >= MIN_PLAUSIBLE_PITCH_DEG && pitchDeg <= MAX_PLAUSIBLE_PITCH_DEG;
  }

  private int countSampled(DepthPointCloud cloud) {
    int sampled = 0;
    for (int gy = 0; gy < cloud.getGridHeight(); gy += SAMPLE_STRIDE) {
      int rowBase = gy * cloud.getGridWidth();
      for (int gx = 0; gx < cloud.getGridWidth(); gx += SAMPLE_STRIDE) {
        if (cloud.isValid(rowBase + gx)) {
          sampled++;
        }
      }
    }
    return sampled;
  }

  private void fillHistogram(DepthPointCloud cloud, float pitchRad) {
    Arrays.fill(histogram, 0);
    float cos = (float) Math.cos(pitchRad);
    float sin = (float) Math.sin(pitchRad);
    for (int gy = 0; gy < cloud.getGridHeight(); gy += SAMPLE_STRIDE) {
      int rowBase = gy * cloud.getGridWidth();
      for (int gx = 0; gx < cloud.getGridWidth(); gx += SAMPLE_STRIDE) {
        int idx = rowBase + gx;
        if (!cloud.isValid(idx)) {
          continue;
        }
        float value = -((cloud.y(idx) * cos) + (cloud.z(idx) * sin));
        int bin = (int) ((value - RANGE_MIN_M) / BIN_SIZE_M);
        if (bin >= 0 && bin < BIN_COUNT) {
          histogram[bin]++;
        }
      }
    }
  }

  /** Faixas percorridas acima do primeiro sinal de piso para achar a moda (0,30 m). */
  private static final int MODE_SEARCH_SPAN_BINS = 15;

  /**
   * Bin de maior massa dentro da região mais baixa com suporte real: a moda do piso.
   *
   * <p>Usar simplesmente o bin mais baixo com massa suficiente enviesa a altura para baixo, porque o
   * ruído axial do Astra cresce com Z² e a cauda inferior do piso distante chega a passar de 10 cm. A
   * moda é insensível a essa cauda. Teto e bancadas também formam picos no pitch correto, mas ficam
   * acima da janela de busca.
   */
  private int findFloorModeBin(int minSupport) {
    int firstSignal = -1;
    for (int bin = 0; bin < BIN_COUNT - 1; bin++) {
      if (histogram[bin] + histogram[bin + 1] >= minSupport) {
        firstSignal = bin;
        break;
      }
    }
    if (firstSignal < 0) {
      return -1;
    }
    int bestBin = firstSignal;
    int bestCount = histogram[firstSignal];
    int lastBin = Math.min(BIN_COUNT - 1, firstSignal + MODE_SEARCH_SPAN_BINS);
    for (int bin = firstSignal + 1; bin <= lastBin; bin++) {
      if (histogram[bin] > bestCount) {
        bestCount = histogram[bin];
        bestBin = bin;
      }
    }
    return bestBin;
  }

  /** Massa do pico com os vizinhos imediatos: mede o quão plano o piso ficou neste pitch. */
  private int binScore(int floorBin) {
    int score = histogram[floorBin];
    if (floorBin > 0) {
      score += histogram[floorBin - 1];
    }
    if (floorBin + 1 < BIN_COUNT) {
      score += histogram[floorBin + 1];
    }
    return score;
  }

  /**
   * Refina a altura do piso com a média dos pontos dentro da faixa do bin vencedor.
   *
   * <p>Sem isso a altura fica quantizada em 2 cm, o que já é suficiente para segmentar mas atrapalha
   * a medição de altura corporal.
   */
  private float refineFloorValue(DepthPointCloud cloud, float pitchRad, float coarseFloor) {
    float cos = (float) Math.cos(pitchRad);
    float sin = (float) Math.sin(pitchRad);
    float lowerBound = coarseFloor - (BIN_SIZE_M * 1.5f);
    float upperBound = coarseFloor + (BIN_SIZE_M * 2.5f);
    double sum = 0;
    int count = 0;
    for (int gy = 0; gy < cloud.getGridHeight(); gy += SAMPLE_STRIDE) {
      int rowBase = gy * cloud.getGridWidth();
      for (int gx = 0; gx < cloud.getGridWidth(); gx += SAMPLE_STRIDE) {
        int idx = rowBase + gx;
        if (!cloud.isValid(idx)) {
          continue;
        }
        float value = -((cloud.y(idx) * cos) + (cloud.z(idx) * sin));
        if (value >= lowerBound && value <= upperBound) {
          sum += value;
          count++;
        }
      }
    }
    return count > 0 ? (float) (sum / count) : coarseFloor;
  }

  public boolean didFitSucceed() {
    return lastFitSucceeded;
  }

  /** Número de pontos que sustentaram o piso escolhido; útil para diagnóstico no overlay. */
  public int getFloorSupport() {
    return lastFloorSupport;
  }
}
