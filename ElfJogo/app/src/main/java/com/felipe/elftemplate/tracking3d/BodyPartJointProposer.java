package com.felipe.elftemplate.tracking3d;

import java.util.Arrays;

/**
 * Converte nuvens de células rotuladas em posições de junta, por mean shift ponderado em 3D.
 *
 * <p>Última etapa do pipeline do Kinect. A classificação por pixel dá uma nuvem por parte do corpo;
 * a junta é o <b>modo</b> dessa nuvem, não a média. A distinção importa: com o braço em frente ao
 * tronco, alguns pixels de mão são classificados errado no torso, e a média seria puxada para lá. O
 * mean shift converge para o pico local de densidade e ignora a cauda.
 *
 * <p><b>Peso proporcional à área real.</b> Cada célula entra com a probabilidade da parte multiplicada
 * por Z², porque uma célula da grade cobre uma área do mundo que cresce com o quadrado da distância.
 * Sem isso, uma pessoa perto teria voto muito mais forte que uma longe e os limiares de confiança
 * deixariam de valer em toda a sala. Shotton usa a mesma ponderação por profundidade ao quadrado.
 *
 * <p><b>Da casca para o centro.</b> O classificador só vê a superfície visível do corpo, então o modo
 * cai na face voltada para a câmera. O modo é empurrado para dentro pelo raio antropométrico do
 * segmento ({@link BodyPart#surfaceToCenterM}), colocando a junta no centro articular.
 */
public final class BodyPartJointProposer {

  /** Iterações do mean shift. Converge rápido porque parte do centróide ponderado. */
  private static final int MEAN_SHIFT_ITERATIONS = 4;

  /** Largura de banda do núcleo, como fração da estatura. */
  private static final float BANDWIDTH_RATIO = 0.075f;

  /** Piso da largura de banda, para não degenerar com pessoa pequena e longe. */
  private static final float MIN_BANDWIDTH_M = 0.09f;

  /** Fração da área esperada da parte que basta para confiança cheia; o resto é auto-oclusão. */
  private static final float VISIBLE_AREA_SHARE = 0.5f;

  /** Mínimo absoluto de células para uma proposta existir. Abaixo disso é ruído. */
  private static final int MIN_SUPPORT_CELLS = 3;

  private final float[] jointX = new float[MetricSkeleton.JOINT_COUNT];
  private final float[] jointY = new float[MetricSkeleton.JOINT_COUNT];
  private final float[] jointZ = new float[MetricSkeleton.JOINT_COUNT];
  private final float[] jointConfidence = new float[MetricSkeleton.JOINT_COUNT];

  private int proposalCount;

  /** Acumuladores do mean shift, reutilizados entre partes e frames. */
  private double sumX;

  private double sumY;
  private double sumZ;
  private double sumWeight;

  /**
   * Propõe juntas para todas as partes que mapeiam junta e têm suporte.
   *
   * @param statureM estatura estimada, usada para dimensionar a largura de banda
   * @return quantidade de juntas propostas
   */
  public int propose(
      DepthPointCloud cloud,
      MetricBodySegmenter segmenter,
      BodyPartLabeler labeler,
      BodyCluster cluster,
      float statureM) {
    reset();
    if (cloud == null || segmenter == null || labeler == null || cluster == null) {
      return 0;
    }
    float bandwidth = Math.max(MIN_BANDWIDTH_M, BANDWIDTH_RATIO * statureM);
    for (int part = 0; part < BodyPart.COUNT; part++) {
      int joint = BodyPart.jointOf(part);
      if (joint == BodyPart.NO_JOINT || labeler.cellCountOf(part) < MIN_SUPPORT_CELLS) {
        continue;
      }
      proposePart(cloud, segmenter, labeler, cluster, part, joint, bandwidth);
    }
    return proposalCount;
  }

  public void reset() {
    proposalCount = 0;
    Arrays.fill(jointConfidence, 0f);
  }

  /** Localiza o modo da nuvem de uma parte e grava a junta correspondente. */
  private void proposePart(
      DepthPointCloud cloud,
      MetricBodySegmenter segmenter,
      BodyPartLabeler labeler,
      BodyCluster cluster,
      int part,
      int joint,
      float bandwidth) {
    if (!accumulateAll(cloud, segmenter, labeler, cluster, part)) {
      return;
    }
    float modeX = (float) (sumX / sumWeight);
    float modeY = (float) (sumY / sumWeight);
    float modeZ = (float) (sumZ / sumWeight);
    for (int iteration = 0; iteration < MEAN_SHIFT_ITERATIONS; iteration++) {
      if (!accumulateWithin(
          cloud, segmenter, labeler, cluster, part, modeX, modeY, modeZ, bandwidth)) {
        break;
      }
      modeX = (float) (sumX / sumWeight);
      modeY = (float) (sumY / sumWeight);
      modeZ = (float) (sumZ / sumWeight);
    }
    float confidence = confidenceOf(cloud, labeler, part, modeZ);
    if (confidence <= 0f) {
      return;
    }
    jointX[joint] = modeX;
    jointY[joint] = modeY;
    // Empurra da casca visível para o centro do segmento: +Z é para longe da câmera.
    jointZ[joint] = modeZ + BodyPart.surfaceToCenterM(part);
    jointConfidence[joint] = confidence;
    proposalCount++;
  }

  /** Centróide ponderado de toda a nuvem da parte: ponto de partida do mean shift. */
  private boolean accumulateAll(
      DepthPointCloud cloud,
      MetricBodySegmenter segmenter,
      BodyPartLabeler labeler,
      BodyCluster cluster,
      int part) {
    clearAccumulators();
    int gridWidth = cloud.getGridWidth();
    for (int gy = cluster.minGridY; gy <= cluster.maxGridY; gy++) {
      int rowBase = gy * gridWidth;
      for (int gx = cluster.minGridX; gx <= cluster.maxGridX; gx++) {
        int cell = rowBase + gx;
        float vote = labeler.weightForPart(cell, part);
        if (vote <= 0f) {
          continue;
        }
        addCell(cloud, segmenter, cell, vote);
      }
    }
    return sumWeight > 0d;
  }

  /**
   * Reacumula apenas as células dentro da largura de banda do modo atual, com núcleo gaussiano.
   *
   * <p>É o passo do mean shift: a cada iteração o centro é recalculado usando somente a vizinhança,
   * então células distantes de outra parte do corpo deixam de influenciar.
   */
  private boolean accumulateWithin(
      DepthPointCloud cloud,
      MetricBodySegmenter segmenter,
      BodyPartLabeler labeler,
      BodyCluster cluster,
      int part,
      float centerX,
      float centerY,
      float centerZ,
      float bandwidth) {
    clearAccumulators();
    float bandwidthSquared = bandwidth * bandwidth;
    int gridWidth = cloud.getGridWidth();
    for (int gy = cluster.minGridY; gy <= cluster.maxGridY; gy++) {
      int rowBase = gy * gridWidth;
      for (int gx = cluster.minGridX; gx <= cluster.maxGridX; gx++) {
        int cell = rowBase + gx;
        float vote = labeler.weightForPart(cell, part);
        if (vote <= 0f) {
          continue;
        }
        float dx = cloud.x(cell) - centerX;
        float dy = segmenter.worldYAt(cell) - centerY;
        float dz = segmenter.worldZAt(cell) - centerZ;
        float distanceSquared = (dx * dx) + (dy * dy) + (dz * dz);
        if (distanceSquared > bandwidthSquared) {
          continue;
        }
        addCell(
            cloud,
            segmenter,
            cell,
            vote * (float) Math.exp(-2d * distanceSquared / bandwidthSquared));
      }
    }
    return sumWeight > 0d;
  }

  /** Acumula uma célula com peso = voto na parte × área real da célula (Z²). */
  private void addCell(
      DepthPointCloud cloud, MetricBodySegmenter segmenter, int cell, float vote) {
    float depth = cloud.z(cell);
    float weight = vote * depth * depth;
    sumX += cloud.x(cell) * weight;
    sumY += segmenter.worldYAt(cell) * weight;
    sumZ += segmenter.worldZAt(cell) * weight;
    sumWeight += weight;
  }

  private void clearAccumulators() {
    sumX = 0d;
    sumY = 0d;
    sumZ = 0d;
    sumWeight = 0d;
  }

  /**
   * Confiança pela fração da área esperada da parte que foi efetivamente vista.
   *
   * <p>A área esperada vem do raio antropométrico do segmento; a área de uma célula vem dos
   * intrínsecos naquela distância. A razão entre células observadas e esperadas é uma grandeza
   * geométrica, então o mesmo limiar de confiança vale a 1 m e a 3,5 m. Contar células cruas não
   * funcionaria: uma mão a 3,5 m ocupa poucas células mesmo quando perfeitamente visível.
   */
  private float confidenceOf(
      DepthPointCloud cloud, BodyPartLabeler labeler, int part, float depthMeters) {
    float cellSize = cloud.cellSizeMetersAt(Math.max(0.3f, depthMeters));
    float cellArea = cellSize * cellSize;
    if (cellArea <= 0f) {
      return 0f;
    }
    float radius = BodyPart.surfaceToCenterM(part);
    float partArea = (float) Math.PI * radius * radius;
    float expectedCells = (partArea / cellArea) * VISIBLE_AREA_SHARE;
    if (expectedCells < MIN_SUPPORT_CELLS) {
      expectedCells = MIN_SUPPORT_CELLS;
    }
    float ratio = labeler.cellCountOf(part) / expectedCells;
    return ratio > 1f ? 1f : ratio;
  }

  public boolean hasProposal(int joint) {
    return jointConfidence[joint] > 0f;
  }

  public float confidenceOf(int joint) {
    return jointConfidence[joint];
  }

  public float x(int joint) {
    return jointX[joint];
  }

  public float y(int joint) {
    return jointY[joint];
  }

  public float z(int joint) {
    return jointZ[joint];
  }

  public int getProposalCount() {
    return proposalCount;
  }
}
